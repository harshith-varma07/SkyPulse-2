package com.air.airquality.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BCryptHashGenerator {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java BCryptHashGenerator <plainPassword>");
            return;
        }
        String plainPassword = args[0];
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String hashed = encoder.encode(plainPassword);
        System.out.println("BCrypt hash: " + hashed);
    }
}
