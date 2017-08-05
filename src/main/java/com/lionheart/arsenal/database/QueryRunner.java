package com.lionheart.arsenal.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Created by wangyiguang on 17/8/3.
 */
public class QueryRunner {

    private JDBCDataSource dataSource;
    private String user;
    private String passwd;
    private Connection connection;

    public QueryRunner(JDBCDataSource dataSource, String user, String passwd) {
        this.dataSource = dataSource;
        this.user = user;
        this.passwd = passwd;
    }

    public ResultSet run(String query) {
        ResultSet result = null;
        try {
            connection = dataSource.getConnection(user, passwd);
            Statement statement = connection.createStatement();
            result = statement.executeQuery(query);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
