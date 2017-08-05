package com.lionheart.arsenal.database;

/**
 * Created by wangyiguang on 17/8/3.
 */
public class MysqlDataSource extends JDBCDataSource {

    public MysqlDataSource(String host, String port) {
        super(host, port);
        this.driver = "com.mysql.jdbc.Driver";
        this.prefix = "mysql";
    }

    public MysqlDataSource(String host, String port, String database) {
        super(host, port, database);
        this.driver = "com.mysql.jdbc.Driver";
        this.prefix = "mysql";
    }
}
