package com.smartleave.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generates the BCrypt hashes stored in {@code database/seed.sql}.
 *
 * <p>Run with:</p>
 * <pre>
 *   mvn -q test-compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt
 *   java -cp "target/test-classes:target/classes:$(cat target/cp.txt)" \
 *        com.smartleave.util.BcryptHashGenerator
 * </pre>
 *
 * <p>The demo passwords themselves are documented in the README; only the
 * hashes are committed.</p>
 */
public final class BcryptHashGenerator {

    private BcryptHashGenerator() {
    }

    public static void main(String[] args) {
        Map<String, String> demoAccounts = new LinkedHashMap<>();
        demoAccounts.put("admin@smartleave.com", "Admin@123");
        demoAccounts.put("manager1@smartleave.com", "Manager@123");
        demoAccounts.put("manager2@smartleave.com", "Manager@123");
        demoAccounts.put("employee1@smartleave.com", "Employee@123");
        demoAccounts.put("employee2@smartleave.com", "Employee@123");
        demoAccounts.put("employee3@smartleave.com", "Employee@123");
        demoAccounts.put("employee4@smartleave.com", "Employee@123");
        demoAccounts.put("employee5@smartleave.com", "Employee@123");
        demoAccounts.put("employee6@smartleave.com", "Employee@123");
        demoAccounts.put("employee7@smartleave.com", "Employee@123");
        demoAccounts.put("employee8@smartleave.com", "Employee@123");

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
        for (Map.Entry<String, String> account : demoAccounts.entrySet()) {
            System.out.println(account.getKey() + " -> " + account.getValue()
                    + " -> " + encoder.encode(account.getValue()));
        }
    }
}
