package com.lionheart.arsenal.database;

import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Created by wangyiguang on 17/8/3.
 */
public class JDBCDataSource {
    protected String host;
    protected String port;
    protected String database;
    protected String driver;
    protected String prefix;

    public JDBCDataSource(String host, String port) {
        this.host = host;
        this.port = port;
        this.database = "default";
    }

    public JDBCDataSource(String host, String port, String database) {
        this.host = host;
        this.port = port;
        this.database = database;
    }

    public Connection getConnection(String user, String password) {
        Connection connection = null;
        try {
            Class.forName(driver);
            String url = "jdbc:" + prefix + "://" + host + ":" + port + "/" + database;
            connection = DriverManager.getConnection(url, user, password);
        } catch (ClassNotFoundException cnfe) {
            System.out.println("Get connection failed because Database Driver not found.");
        } catch (SQLException se) {
            se.printStackTrace();
        }
        return connection;
    }
}
