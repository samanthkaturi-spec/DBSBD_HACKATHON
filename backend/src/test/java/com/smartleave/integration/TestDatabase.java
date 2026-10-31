package com.smartleave.integration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Prepares an <b>isolated test database</b> ({@code smart_leave_management_test})
 * so automated tests never touch the demonstration data.
 *
 * <p>Connection settings come from the same environment variables as the
 * application, so a developer points the tests at their own MySQL server with
 * {@code DB_HOST / DB_PORT / DB_USER / DB_PASSWORD}. The default is
 * {@code localhost:3306}.</p>
 *
 * <p>The canonical scripts in {@code database/} are reused, with the schema
 * name rewritten to the test database, which guarantees the tests exercise
 * exactly the schema that ships to MySQL Workbench.</p>
 */
public final class TestDatabase {

    public static final String DB_NAME = "smart_leave_management_test";
    public static final String HOST = env("DB_HOST", "localhost");
    public static final String PORT = env("DB_PORT", "3306");
    public static final String USER = env("DB_USER", "root");
    public static final String PASSWORD = env("DB_PASSWORD", "");

    public static final String URL = "jdbc:mysql://" + HOST + ":" + PORT + "/" + DB_NAME
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";

    private static final String ADMIN_URL = "jdbc:mysql://" + HOST + ":" + PORT
            + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&createDatabaseIfNotExist=true";

    private static final AtomicBoolean READY = new AtomicBoolean(false);

    private TestDatabase() {
    }

    /** Drops and rebuilds the test database. Only the first call in a JVM does work. */
    public static void reset() {
        if (!READY.compareAndSet(false, true)) {
            return;
        }
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("MySQL driver not on the test classpath", ex);
        }
        try (Connection connection = DriverManager.getConnection(ADMIN_URL, USER, PASSWORD)) {
            execute(connection, "DROP DATABASE IF EXISTS " + DB_NAME);
            runScript(connection, "/db/schema.sql");
            runScript(connection, "/db/seed.sql");
        } catch (SQLException ex) {
            READY.set(false);
            throw new IllegalStateException(
                    "Could not prepare the isolated test database " + DB_NAME
                            + " on " + HOST + ":" + PORT
                            + " - set DB_HOST/DB_PORT/DB_USER/DB_PASSWORD and make sure MySQL is running",
                    ex);
        }
    }

    private static void runScript(Connection connection, String classpathResource) throws SQLException {
        String script = readResource(classpathResource)
                // point the canonical script at the isolated test schema
                .replace("smart_leave_management", DB_NAME);
        for (String statement : splitStatements(script)) {
            execute(connection, statement);
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    /** Strips {@code --} comments and splits on statement terminators. */
    private static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\n")) {
            if (line.trim().startsWith("--")) {
                continue;
            }
            current.append(line).append('\n');
            if (line.trim().endsWith(";")) {
                String candidate = current.toString().trim();
                if (!candidate.isEmpty()) {
                    statements.add(candidate);
                }
                current.setLength(0);
            }
        }
        String tail = current.toString().trim();
        if (!tail.isEmpty()) {
            statements.add(tail);
        }
        return statements;
    }

    private static String readResource(String path) {
        try (InputStream stream = TestDatabase.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Classpath resource not found: " + path);
            }
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append('\n');
                }
            }
            return content.toString();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read " + path, ex);
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
