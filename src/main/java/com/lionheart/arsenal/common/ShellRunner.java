package com.lionheart.arsenal.common;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import java.util.List;

/**
 * Created by wangyiguang on 17/7/29.
 */
public class ShellRunner {
    private ProcessBuilder builder;
    private String[] commands;
    private String workDirectory;
    private StringBuffer stdout;

    public ShellRunner(String cmd, String directory) {
        commands = cmd.split(" ");
        workDirectory = directory;
        builder = new ProcessBuilder(commands);
    }

    public StringBuffer exec() throws IOException {
        InputStream in;
        builder.directory(new File(workDirectory));
        builder.redirectErrorStream(true);
        Process process = builder.start();
        in = process.getInputStream();
        stdout = new StringBuffer();
        byte[] re = new byte[1024];
        while (in.read(re) != -1) {
            stdout = stdout.append(new String(re));
        }
        if (in != null) {
            in.close();
        }
        return stdout;
    }
}
