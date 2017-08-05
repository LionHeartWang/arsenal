package com.lionheart.arsenal.network;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mortbay.jetty.*;
import org.mortbay.jetty.handler.ContextHandlerCollection;
import org.mortbay.jetty.handler.HandlerCollection;
import org.mortbay.jetty.handler.RequestLogHandler;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.security.SslSocketConnector;
import org.mortbay.jetty.servlet.*;
import org.mortbay.jetty.webapp.WebAppContext;
import org.mortbay.thread.QueuedThreadPool;
import org.mortbay.thread.ThreadPool;
import org.mortbay.util.MultiException;

import javax.servlet.http.HttpServlet;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.*;


/**
 * Created by wangyiguang on 17/8/2.
 * A Jetty embedded server to answer http requests. The primary goal
 * is to serve up status information for the server.
 * There are three contexts:
 * "/logs/" -> points to the log directory
 * "/static/" -> points to common static files (src/webapps/static)
 * "/" -> the jsp server code from (src/webapps/<name>)
 */
public class HttpServer {

    public static final Log LOG = LogFactory.getLog(HttpServer.class);

    protected final Server webServer;
    protected final RequestLogHandler requestLogHandler;
    protected final Connector listener;
    protected final WebAppContext webAppContext;
    protected final boolean findPort;
    protected final Map<Context, Boolean> defaultContexts = new HashMap<Context, Boolean>();
    protected final List<String> filterNames = new ArrayList<String>();
    private static final int MAX_RETRIES = 10;

    public HttpServer(Configuration configuration) throws IOException {
        this("server",
                "0.0.0.0",
                configuration.getInt("http.port", 8899),
                true,
                configuration);
    }

    /**
     * Create a status server on the given port.
     * The jsp scripts are taken from src/webapps/<name>.
     *
     * @param name     The name of the server
     * @param port     The port to use on the server
     * @param findPort whether the server should start at the given port and
     *                 increment by 1 until it finds a free port.
     * @param conf     Configuration
     */
    public HttpServer(String name, String bindAddress, int port, boolean findPort, Configuration conf) throws
            IOException {
        webServer = new Server();
        this.findPort = findPort;

        listener = createBaseListener(conf);
        listener.setHost(bindAddress);
        listener.setPort(port);
        listener.setStatsOn(true);
        webServer.addConnector(listener);

        QueuedThreadPool qtp = new QueuedThreadPool();
        qtp.setMaxStopTimeMs(0);
        webServer.setThreadPool(qtp);


        final String appDir = getWebAppsPath();
        HandlerCollection handlers = new HandlerCollection();
        ContextHandlerCollection contexts = new ContextHandlerCollection();
        requestLogHandler = new RequestLogHandler();
        handlers.setHandlers(new Handler[]{contexts, requestLogHandler});
        webServer.setHandler(handlers);

        webAppContext = new WebAppContext();
        webAppContext.setContextPath("/");
        webAppContext.setWar(appDir + "/" + name);
        String[] welcomeFiles = new String[1];
        welcomeFiles[0] = "index.html";
        webAppContext.setWelcomeFiles(welcomeFiles);
        webServer.addHandler(webAppContext);

        addDefaultApps(contexts, appDir);
    }

    /**
     * Create a required listener for the Jetty instance listening on the port
     * provided. This wrapper and all subclasses must create at least one
     * listener.
     */
    protected Connector createBaseListener(Configuration conf) throws IOException {
        SelectChannelConnector ret = new SelectChannelConnector();
        ret.setLowResourceMaxIdleTime(10000);
        ret.setAcceptQueueSize(conf.getInt("http.server.listen.queue.size", 1024));
        ret.setResolveNames(false);
        ret.setUseDirectBuffers(false);
        return ret;
    }

    /**
     * Add default apps.
     *
     * @param appDir The application directory
     * @throws IOException
     */
    protected void addDefaultApps(ContextHandlerCollection parent, final String appDir) throws IOException {
        // set up the context for "/static/*"
        Context staticContext = new Context(parent, "/static");
        staticContext.setResourceBase(appDir + "/static");
        staticContext.addServlet(DefaultServlet.class, "/*");
        defaultContexts.put(staticContext, true);

        Context fontContext = new Context(parent, "/fonts");
        fontContext.setResourceBase(appDir + "/fonts");
        fontContext.addServlet(DefaultServlet.class, "/*");
        defaultContexts.put(fontContext, true);
    }

    public void addContext(Context ctxt, boolean isFiltered) throws IOException {
        webServer.addHandler(ctxt);
        defaultContexts.put(ctxt, isFiltered);
    }

    /**
     * Add a context
     *
     * @param pathSpec   The path spec for the context
     * @param dir        The directory containing the context
     * @param isFiltered if true, the servlet is added to the filter path mapping
     * @throws IOException
     */
    protected void addContext(String pathSpec, String dir, boolean isFiltered) throws IOException {
        if (0 == webServer.getHandlers().length) {
            throw new RuntimeException("Couldn't find handler");
        }
        WebAppContext webAppCtx = new WebAppContext();
        webAppCtx.setContextPath(pathSpec);
        webAppCtx.setWar(dir);
        addContext(webAppCtx, true);
    }

    /**
     * Set a value in the webapp context. These values are available to the jsp
     * pages as "application.getAttribute(name)".
     *
     * @param name  The name of the attribute
     * @param value The value of the attribute
     */
    public void setAttribute(String name, Object value) {
        webAppContext.setAttribute(name, value);
        // also add it to other contexts
        for (Context context : defaultContexts.keySet()) {
            context.setAttribute(name, value);
        }
    }

    /**
     * Add a servlet in the server.
     *
     * @param name     The name of the servlet (can be passed as null)
     * @param pathSpec The path spec for the servlet
     * @param clazz    The servlet class
     */
    public void addServlet(String name, String pathSpec, Class<? extends HttpServlet> clazz) {
        addInternalServlet(name, pathSpec, clazz);
        addFilterPathMapping(pathSpec, webAppContext);
    }

    /**
     * Add an internal servlet in the server.
     *
     * @param name     The name of the servlet (can be passed as null)
     * @param pathSpec The path spec for the servlet
     * @param clazz    The servlet class
     * @deprecated this is a temporary method
     */
    @Deprecated
    public void addInternalServlet(String name, String pathSpec, Class<? extends HttpServlet> clazz) {
        ServletHolder holder = new ServletHolder(clazz);
        if (name != null) {
            holder.setName(name);
        }
        webAppContext.addServlet(holder, pathSpec);
    }

    /**
     * {@inheritDoc}
     */
    public void addFilter(String name, String classname, Map<String, String> parameters) {
        final String[] userFacingUrls = {"*.html", "*.jsp"};
        defineFilter(webAppContext, name, classname, parameters, userFacingUrls);
        final String[] allUrls = {"/*"};
        for (Map.Entry<Context, Boolean> e : defaultContexts.entrySet()) {
            if (e.getValue()) {
                Context ctx = e.getKey();
                defineFilter(ctx, name, classname, parameters, allUrls);
                LOG.info("Added filter " + name + " (class=" + classname + ") to context " + ctx.getDisplayName());
            }
        }
        filterNames.add(name);
    }

    /**
     * {@inheritDoc}
     */
    public void addGlobalFilter(String name, String classname, Map<String, String> parameters) {
        final String[] allUrls = {"/*"};
        defineFilter(webAppContext, name, classname, parameters, allUrls);
        for (Context ctx : defaultContexts.keySet()) {
            defineFilter(ctx, name, classname, parameters, allUrls);
        }
        LOG.info("Added global filter" + name + " (class=" + classname + ")");
    }

    /**
     * Define a filter for a context and set up default url mappings.
     */
    protected void defineFilter(Context ctx, String name, String classname, Map<String, String> parameters, String[]
            urls) {

        FilterHolder holder = new FilterHolder();
        holder.setName(name);
        holder.setClassName(classname);
        holder.setInitParameters(parameters);
        FilterMapping fmap = new FilterMapping();
        fmap.setPathSpecs(urls);
        fmap.setDispatches(Handler.ALL);
        fmap.setFilterName(name);
        ServletHandler handler = ctx.getServletHandler();
        handler.addFilter(holder, fmap);
    }

    /**
     * Add the path spec to the filter path mapping.
     *
     * @param pathSpec  The path spec
     * @param webAppCtx The WebApplicationContext to add to
     */
    protected void addFilterPathMapping(String pathSpec, Context webAppCtx) {
        ServletHandler handler = webAppCtx.getServletHandler();
        for (String name : filterNames) {
            FilterMapping fmap = new FilterMapping();
            fmap.setPathSpec(pathSpec);
            fmap.setFilterName(name);
            fmap.setDispatches(Handler.ALL);
            handler.addFilterMapping(fmap);
        }
    }

    /**
     * Get the value in the webapp context.
     *
     * @param name The name of the attribute
     * @return The value of the attribute
     */
    public Object getAttribute(String name) {
        return webAppContext.getAttribute(name);
    }

    /**
     * Get the pathname to the webapps files.
     *
     * @return the pathname as a URL
     * @throws IOException if 'webapps' directory cannot be found on CLASSPATH.
     */
    public String getWebAppsPath() throws IOException {
        URL url = getClass().getClassLoader().getResource("webapps");
        if (url == null) {
            throw new IOException("webapps not found in CLASSPATH");
        }
        return url.toString();
    }

    /**
     * Get the port that the server is on
     *
     * @return the port
     */
    public int getPort() {
        return webServer.getConnectors()[0].getLocalPort();
    }

    public void setThreads(int min, int max) {
        QueuedThreadPool pool = (QueuedThreadPool) webServer.getThreadPool();
        pool.setMinThreads(min);
        pool.setMaxThreads(max);
    }


    public void setAcceptorThreads(int num) {
        if (listener instanceof AbstractConnector) {
            ((AbstractConnector) listener).setAcceptors(num);
        }
    }

    public void setAcceptQueueSize(int max) {
        if (listener instanceof AbstractConnector) {
            ((AbstractConnector) listener).setAcceptQueueSize(max);
        }
    }


    /**
     * Configure an ssl listener on the server.
     *
     * @param addr     address to listen on
     * @param keystore location of the keystore
     * @param storPass password for the keystore
     * @param keyPass  password for the key
     */
    @Deprecated
    public void addSslListener(InetSocketAddress addr, String keystore, String storPass, String keyPass) throws
            IOException {
        if (webServer.isStarted()) {
            throw new IOException("Failed to add ssl listener");
        }
        SslSocketConnector sslListener = new SslSocketConnector();
        sslListener.setHost(addr.getHostName());
        sslListener.setPort(addr.getPort());
        sslListener.setKeystore(keystore);
        sslListener.setPassword(storPass);
        sslListener.setKeyPassword(keyPass);
        webServer.addConnector(sslListener);
    }

    /**
     * Start the server. Does not wait for the server to start.
     */
    public void start() throws IOException {
        try {
            int port = 0;
            int oriPort = listener.getPort(); // The original requested port
            while (true) {
                try {
                    port = webServer.getConnectors()[0].getLocalPort();
                    LOG.info("Port returned by webServer.getConnectors()[0]."
                            + "getLocalPort() before open() is " + port
                            + ". Opening the listener on " + oriPort);
                    listener.open();
                    port = listener.getLocalPort();
                    LOG.info("listener.getLocalPort() returned "
                            + listener.getLocalPort()
                            + " webServer.getConnectors()[0].getLocalPort() returned "
                            + webServer.getConnectors()[0].getLocalPort());
                    // Workaround to handle the problem reported in HADOOP-4744
                    if (port < 0) {
                        Thread.sleep(100);
                        int numRetries = 1;
                        while (port < 0) {
                            LOG.warn("listener.getLocalPort returned " + port);
                            if (numRetries++ > MAX_RETRIES) {
                                throw new Exception(" listener.getLocalPort is returning "
                                        + "less than 0 even after " + numRetries + " resets");
                            }
                            for (int i = 0; i < 2; i++) {
                                LOG.info("Retrying listener.getLocalPort()");
                                port = listener.getLocalPort();
                                if (port > 0) {
                                    break;
                                }
                                Thread.sleep(200);
                            }
                            if (port > 0) {
                                break;
                            }
                            LOG.info("Bouncing the listener");
                            listener.close();
                            Thread.sleep(1000);
                            listener.setPort(oriPort == 0 ? 0 : (oriPort += 1));
                            listener.open();
                            Thread.sleep(100);
                            port = listener.getLocalPort();
                        }
                    } // Workaround end
                    LOG.info("Jetty bound to port " + port);
                    webServer.start();
                    break;
                } catch (IOException ex) {
                    // if this is a bind exception,
                    // then try the next port number.
                    LOG.info("IOException: ");
                    ex.printStackTrace();
                    if (ex instanceof BindException) {
                        if (!findPort) {
                            throw (BindException) ex;
                        }
                    } else {
                        LOG.info("HttpServer.start() threw a non Bind IOException");
                        throw ex;
                    }
                } catch (MultiException ex) {
                    LOG.info("HttpServer.start() threw a MultiException");
                    throw ex;
                }
                listener.close();
                listener.setPort((oriPort += 1));
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Problem starting http server", e);
        }
    }

    /**
     * stop the server
     */
    public void stop() throws Exception {
        listener.close();
        webServer.stop();
    }

    public void join() throws InterruptedException {
        webServer.join();
    }

    public int getQueueSize() {
        ThreadPool tp = webServer.getThreadPool();
        if (tp instanceof QueuedThreadPool) {
            return ((QueuedThreadPool) tp).getQueueSize();
        }
        return 0;
    }

    public void addRequestLog(String logDirParent) {
        File logDir = new File(logDirParent, "jetty");
        if (logDir.exists() && logDir.isFile()) {
            logDir.delete();
        }
        if (!logDir.exists()) {
            logDir.mkdirs();
        }

        NCSARequestLog requestLog = new NCSARequestLog(logDir.getAbsolutePath() + "/jetty-yyyy_mm_dd.request.log");
        requestLog.setRetainDays(30);
        requestLog.setAppend(true);
        requestLog.setExtended(false);
        requestLog.setLogTimeZone(TimeZone.getDefault().getID());
        requestLogHandler.setRequestLog(requestLog);
    }

    public int getConnectionsOpen() {
        return listener.getConnectionsOpen();
    }
}
