package com.hoontudy.toby.ch01;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public abstract class UserDao {
    public void add(User user) throws ClassNotFoundException, SQLException{
        Connection c = getConnection();
        // 구현
    }

    public void get(String id) throws ClassNotFoundException, SQLException{
        Connection c = getConnection();
        // 구현
    }

    public  abstract Connection getConnection() throws ClassNotFoundException, SQLException;


    /*
    private Connection getConnection() throws ClassNotFoundException, SQLException{
        Class.forName("com.mysql.jdbc.Driver");
        Connection c = DriverManager.getConnection(
                "jdbc:mysal://localhost/springbook", "spring", "book");
        )
        return c;
    }
    */
}
