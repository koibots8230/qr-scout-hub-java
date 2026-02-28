package com.koibots.scout.hub;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.opencsv.CSVWriter;

/**
 * A QR Scout project is an on-disk collection of information including the
 * game configuration data and the scouting database containing any information
 * saved from the QR scout.
 */
public class Project
{
    private static final String ANALYTICS_SUBDIRECTORY = "analytics";
    private static final String DB_SUBDIRECTORY = "db";

    /**
     * The directory in which the project lives.
     */
    private File directory;

    /**
     * The URL for the database connection.
     */
    private String databaseURL;

    /**
     * The game configuration.
     */
    private GameConfig config;

    /**
     * The list of anylitics
     */
    private List<Analytic> analytics = new ArrayList<>();

    private Project() {
        // Only define a private constructor: clients must use static Factory methods.
    }

    public void setDirectory(File directory) {
        this.directory = directory;
    }

    public File getDirectory() {
        return directory;
    }

    public GameConfig getGameConfig() {
        return config;
    }

    public void setGameConfig(GameConfig config) {
        this.config = config;
    }

    private void setDatabaseURL(String databaseURL) {
        this.databaseURL = databaseURL;
    }

    public void addAnalytic(File file) throws IOException{
        analytics.add(Analytic.loadAnalytic(file));
    }

    private void loadAnalytics() throws IOException {
        File dir = new File(getDirectory(), ANALYTICS_SUBDIRECTORY);
        if (dir.exists()) {
            File files[] = dir.listFiles((path) -> {
                return path.getName().toLowerCase().endsWith(".json");
            });

            for (File file : files) {
                try {
                    addAnalytic(file);
                } catch (IOException ioe) {
                    // Log to stderr but continue
                    ioe.printStackTrace();
                }
            }
        }
    }

    public List<Analytic> getAnalytics() {
        return analytics;
    }

    private String getDatabaseURL() {
        return databaseURL;
    }

    private static boolean _derbyLoaded;

    {
        System.setProperty("derby.stream.error.method", Project.class.getName() + ".getDerbyLogStream");
    }

    /**
     * A utility method to provide Apache Derby with a destination
     * for logging. For now, we just log to stdput.
     *
     * @return
     */
    public static OutputStream getDerbyLogStream() {
        return System.out;
//        return OutputStream.nullOutputStream();
    }

    private static String normalizeColumnName(String column) {
        return column.toUpperCase().replaceAll("[^A-Z]+", "_");
    }

    private static Field getFieldFromSQLColumn(GameConfig config, String sqlColumnName) {
        for(Field field : config.getFields()) {
            if(sqlColumnName.equals(normalizeColumnName(field.getCode()))) {
                return field;
            }
        }

        return null;
    }

    private static String getSQLDataType(String fieldType) {
        if("boolean".equals(fieldType)) {
            return "CHAR(1)";
        } else if("counter".equals(fieldType) || "number".equals(fieldType) || "range".equals(fieldType)) {
            return "INTEGER";
        } else {
            return "VARCHAR(255)";
        }
    }

    private void createDatabase() throws SQLException {
        File dbDir = new File(getDirectory(), DB_SUBDIRECTORY);

        String url = "jdbc:derby:" + dbDir.getAbsolutePath() + ";create=true;";

        try(Connection conn = DriverManager.getConnection(url)) {
            _derbyLoaded = true;

            createTables(getGameConfig(), conn);

            setDatabaseURL("jdbc:derby:" + dbDir.getAbsolutePath()); // without "create"
        }
    }

    // NOTE: Caller is responsible for resource management
    private static void createTables(GameConfig config, Connection conn) throws SQLException {
        StringBuilder sql = new StringBuilder("CREATE TABLE stand_scouting (id INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1), deleted BOOLEAN NOT NULL DEFAULT FALSE");

        for(Field field : config.getFields()) {
            sql.append(", \"") // NOTE: Using explicit " surrounding the column name to protect keywords, etc.
            .append(normalizeColumnName(field.getCode()))
            .append("\" ")
            .append(getSQLDataType(field.getType()))
            ;
        }

        sql.append(")");

        System.out.println("Creating table: " + sql);

        try(PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.executeUpdate();
        }
    }

    /**
     * Gets the number of records in the project database.
     *
     * @return The number of records in the project's database.
     *
     * @throws SQLException If there was a problem counting the records.
     */
    public int getRecordCount() throws SQLException {
        String databaseURL = getDatabaseURL();

        try(Connection conn = DriverManager.getConnection(databaseURL)) {
            return getRecordCount(conn);
        }
    }

    private int getRecordCount(Connection conn) throws SQLException {
        try(PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) AS cnt FROM stand_scouting WHERE deleted = FALSE");
            ResultSet rs = ps.executeQuery();) {

            if(rs.next()) {
                return rs.getInt("cnt");
            } else {
                return 0;
            }
        }
    }

    private void loadDatabase(String databaseURL) throws SQLException {
        if(null == databaseURL)
            return;

        try(Connection conn = DriverManager.getConnection(databaseURL)) {

            _derbyLoaded = true;

            int count = getRecordCount(conn);

            System.out.println("Completed loading database " + databaseURL + " with " + count + " records");

            setDatabaseURL(databaseURL);
        }
    }

    private void verifyDatabase() throws SQLException {
        String databaseURL = getDatabaseURL();
        GameConfig config = getGameConfig();

        System.out.println("Verifying database " + databaseURL);

        if(null == config) {
            throw new IllegalStateException("No game config loaded");
        }
        if(null == databaseURL) {
            throw new IllegalStateException("No database URL");
        }

        System.out.println("Config: " + config);

        boolean hasDeletedField = false;
        try(Connection conn = DriverManager.getConnection(databaseURL)) {

            PreparedStatement ps = null;
            ResultSet rs = null;
            try {
                _derbyLoaded = true;

                // NOTE: stand_scouting is CASE SENSITIVE here
                rs = conn.getMetaData().getColumns(null, "APP", "STAND_SCOUTING", null);

                // Ensure that the db structure matches the project config
                HashMap<String,String> dbFields = new HashMap<String,String>();
                while(rs.next()) {
                    dbFields.put(rs.getString("COLUMN_NAME"), rs.getString("TYPE_NAME"));
                }

                hasDeletedField = dbFields.containsKey("DELETED");

                // Check all columns are defined
                for(Field field : config.getFields()) {
                    String columnName = normalizeColumnName(field.getCode());

                    if(!dbFields.containsKey(columnName)) {
                        throw new IllegalStateException("Config contains field not found in database: " + field.getCode() + " / " + columnName);
                    }
                }

                for(Map.Entry<String,String> entry : dbFields.entrySet()) {
                    String columnName = entry.getKey();

                    if(!"id".equalsIgnoreCase(columnName) && !"deleted".equalsIgnoreCase(columnName)) {
                        Field field = getFieldFromSQLColumn(config, columnName);
                        if(null == field) {
                            throw new IllegalStateException("Database contains field not found in configuration: " + columnName);
                        }
                    }
                }

                if(!hasDeletedField) {
                    ps = conn.prepareStatement("ALTER TABLE stand_scouting ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE");

                    ps.executeUpdate();
                }

                System.out.println("Verification complete; hasDeleted=" + hasDeletedField);
            } finally {
                if(null != ps) try { ps.close(); }
                catch (SQLException sqle) {
                    sqle.printStackTrace();
                }
                if(null != rs) try { rs.close(); }
                catch (SQLException sqle) {
                    sqle.printStackTrace();
                }
            }
        }
    }

    private String getInsertStatement() {
        StringBuilder insert = new StringBuilder("INSERT INTO stand_scouting (");

        Collection<Field> fields = getGameConfig().getFields();

        boolean first = true;
        for(Field field : fields) {
            if(first) { first = false; }
            else { insert.append(','); }

            insert.append('"').append(normalizeColumnName(field.getCode())).append('"');
        }

        insert.append(") VALUES (");

        first = true;
        for(int i=0; i<fields.size(); ++i) {
            if(first) { first = false; }
            else { insert.append(','); }

            insert.append('?');
        }

        insert.append(')');

        return insert.toString();
    }

    private Map<Field,String> parseCodeData(String code) {
        // QR code format is: datum \t datum \t datum with no keys :(

        // NOTE: Use 2-argument version of String.split() to ensure we get blanks at the end
        String[] values = code.split("\\t", 500); // 500 seems like a reasonable limit ;)

        Collection<Field> fields = getGameConfig().getFields();

        if(values.length != fields.size()) {
            throw new IllegalArgumentException("QR code and game config size mismatch: " + values.length + " != " + fields.size());
        }

        HashMap<Field,String> data = new HashMap<Field,String>();

        int index = 0;

        for(Field field : fields) {
            data.put(field, values[index++]);
        }

        return data;
    }

    /**
     * Inserts a record into the database.
     *
     * @param codeData The game data record. Should be tab-separated and have
     *        the same number of fields as the game.
     *
     * @throws SQLException If there is a problem inserting the data.
     */
    public void insertRecord(String codeData) throws SQLException {
        String databaseURL = getDatabaseURL();

        Map<Field,String> data = parseCodeData(codeData);

        System.out.println("Parsed code data: " + data);

        String insertStatement = getInsertStatement();

        System.out.println("Insert statement: " + insertStatement);

        try(Connection conn = DriverManager.getConnection(databaseURL);
            PreparedStatement ps = conn.prepareStatement(insertStatement)) {

            Collection<Field> fields = getGameConfig().getFields();

            int index = 0; // JDBC uses 1-based addressing; we start at 0 and pre-increment
            for(Field field : fields) {
                String datum = data.get(field);

                if("counter".equals(field.getType()) || "number".equals(field.getType()) || "range".equals(field.getType())) {
                    if(null == datum) {
                        ps.setNull(++index, Types.INTEGER);
                    } else {
                        ps.setInt(++index, Integer.parseInt(datum));
                    }
                } else if("boolean".equals(field.getType())) {
                        if(null == datum) {
                            ps.setNull(++index, Types.BOOLEAN);
                        } else {
                            ps.setString(++index, "true".equals(datum) ? "Y" : "N");
                        }
                } else {
                    if(null == datum) {
                        ps.setString(++index, null);
                    } else {
                        ps.setString(++index, datum);
                    }
                }
            }

            ps.executeUpdate();
        }
    }

    public List<String[]> getRecords() throws SQLException {
        try (Connection conn = DriverManager.getConnection(getDatabaseURL());
             PreparedStatement ps = conn.prepareStatement(getSelectAllStatement());
             ResultSet rs = ps.executeQuery()) {

            ArrayList<String[]> rows = new ArrayList<String[]>();

            ResultSetMetaData rsmd = rs.getMetaData();

            final int columnCount = rsmd.getColumnCount();
            String[] data = new String[columnCount];
            for(int i=0; i < columnCount; ++i) {
                String columnName = rsmd.getColumnName(i+1);
                // The "id" field doesn't have a Field
                if("id".equalsIgnoreCase(columnName)) {
                    data[i] = "id";
                } else if("deleted".equalsIgnoreCase(columnName)) {
                    data[i] = "deleted";
                } else {
                    Field field = getFieldFromSQLColumn(config, columnName);

                    data[i] = field.getTitle();
                }
            }
            rows.add(data);

            while(rs.next()) {
                data = new String[columnCount];
                for(int i=0; i < columnCount; ++i) {
                    data[i] = rs.getString(i+1);
                }

                rows.add(data);
            }

            return rows;
        }
    }

    private String getSelectAllStatement() {
        StringBuilder select = new StringBuilder("SELECT id, deleted");

        Collection<Field> fields = getGameConfig().getFields();

        for(Field field : fields) {
            select
            .append(',')
            .append('"')
            .append(normalizeColumnName(field.getCode()))
            .append('"');
        }

        select.append(" FROM stand_scouting ORDER BY id");

        return select.toString();
    }

    public void updateRecord(String[] record) throws SQLException {
        String updateStatement = getUpdateStatement();

        System.out.println("Update statement: " + updateStatement);

        try(Connection conn = DriverManager.getConnection(getDatabaseURL());
            PreparedStatement ps = conn.prepareStatement(updateStatement)) {

            Collection<Field> fields = getGameConfig().getFields();

            int index = 0; // JDBC uses 1-based addressing; we start at 0 and pre-increment
            int i = 1; // Skip the "id" field
            for(Field field : fields) {
                String datum;
                if(null == record[i]) {
                    datum = null;
                } else {
                    datum = String.valueOf(record[i]);
                }

                ++i;

                if("counter".equals(field.getType()) || "number".equals(field.getType()) || "range".equals(field.getType())) {
                    if(null == datum) {
                        ps.setNull(++index, Types.INTEGER);
                    } else {
                        ps.setInt(++index, Integer.parseInt(datum));
                    }
                } else if("boolean".equals(field.getType())) {
                        if(null == datum) {
                            ps.setNull(++index, Types.BOOLEAN);
                        } else {
                            ps.setString(++index, "true".equals(datum) ? "Y" : "N");
                        }
                } else {
                    if(null == datum) {
                        ps.setString(++index, null);
                    } else {
                        ps.setString(++index, datum);
                    }
                }
            }
            ps.setInt(++index, Integer.parseInt(record[0]));

            ps.executeUpdate();
        }
    }

    private String getUpdateStatement() {
        StringBuilder update = new StringBuilder("UPDATE stand_scouting SET ");

        Collection<Field> fields = getGameConfig().getFields();

        boolean first = true;
        for(Field field : fields) {
            if(first) { first = false; }
            else { update.append(','); }

            update.append('"').append(normalizeColumnName(field.getCode())).append("\"=?");
        }

        update.append(" WHERE id=?");

        return update.toString();
    }

    public void deleteRecords(Collection<Integer> rowIds) throws SQLException {
        try(Connection conn = DriverManager.getConnection(getDatabaseURL());
            PreparedStatement ps = conn.prepareStatement("UPDATE stand_scouting SET deleted=TRUE WHERE id=?")) {
            for(int rowId : rowIds) {
                ps.setInt(1, rowId);

                ps.addBatch();
            }

            ps.executeBatch();
        }
    }

    /**
     * Exports the database records as CSV.
     *
     * @param out The Writer to write to.
     *
     * @throws IOException If there is an IO error.
     * @throws SQLException If there is a database error.
     */
    public void exportDatabase(Writer out) throws IOException, SQLException {
        StringBuilder sql = new StringBuilder("SELECT ");

        GameConfig config = getGameConfig();
        Collection<Field> fields = config.getFields();

        if(null == fields || fields.isEmpty()) {
            sql.append("*"); // Got any better ideas?
        } else {
            boolean first = true;
            for(Field field : fields) {
                if(first) { first = false; }
                else { sql.append(','); }

                sql.append('"').append(normalizeColumnName(field.getCode())).append('"');
            }
        }
        sql.append(" FROM stand_scouting WHERE deleted=FALSE");

        try (Connection conn = DriverManager.getConnection(getDatabaseURL());
             PreparedStatement ps = conn.prepareStatement(sql.toString());
             ResultSet rs = ps.executeQuery()) {

            ResultSetMetaData rsmd = rs.getMetaData();

            try (CSVWriter csv = new CSVWriter(out)) {
                String[] data = new String[rsmd.getColumnCount()];
                for(int i=0; i < rsmd.getColumnCount(); ++i) {
                    String columnName = rsmd.getColumnName(i+1);
                    Field field = getFieldFromSQLColumn(config, columnName);

                    if(null != field) {
                        data[i] = field.getTitle();
                    } else {
                        data[i] = columnName;
                    }
                }
                csv.writeNext(data);

                while(rs.next()) {
                    for(int i=0; i < rsmd.getColumnCount(); ++i) {
                        data[i] = rs.getString(i+1);
                    }

                    csv.writeNext(data);
                }
            }
        }
    }

    private void readConfig() throws IOException {
        File dir = getDirectory();

        setGameConfig(GameConfig.readFile(new File(dir, "config.json")));
    }

    @Override
    public String toString() {
        return "Project { dir=" + getDirectory() + ", game=" + getGameConfig().getPageTitle() + " }";
    }

    public void validateQuery(String sql)
        throws IOException, SQLException
    {
        try (Connection conn = DriverManager.getConnection(getDatabaseURL());
                PreparedStatement ps = conn.prepareStatement(sql)) {

            conn.setReadOnly(true);
            ps.setMaxRows(0);

            try {
                ps.executeQuery();
            } finally {
                conn.setReadOnly(false);
            }
        }

    }

    public List<Object[]> queryDatabase(String sql)
        throws IOException, SQLException
    {
        System.out.println("Running query: " + sql);

        try (Connection conn = DriverManager.getConnection(getDatabaseURL());
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            ArrayList<Object[]> rows = new ArrayList<>();

            conn.setReadOnly(true);

            try {
                ResultSetMetaData rsmd = rs.getMetaData();

                final int columnCount = rsmd.getColumnCount();
                Object[] headers = new String[columnCount];

                for(int i=0; i<columnCount; ) {
                    headers[i] = rsmd.getColumnLabel(++i);
                }

                rows.add(headers);

                while(rs.next()) {
                    Object[] data = new Object[columnCount];

                    for(int i=0; i < rsmd.getColumnCount(); ) {
                        data[i] = rs.getObject(++i);
                    }

                    rows.add(data);
                }
            } finally {
                conn.setReadOnly(false);
            }

            return rows;
        }
    }

    /**
     * Creates a new project.
     *
     * @param directory The directory where the project should be created. Must not exist.
     * @param configFile The JSON configuration file for the FRC game.
     *
     * @return The created Project.
     *
     * @throws IOException If there was a problem creating the Project.
     */
    public static Project createProject(File directory, File configFile) throws IOException {
        GameConfig config;
        if(null == configFile) {
            // Use an empty config
            config = new GameConfig();
        } else {
            config = GameConfig.readFile(configFile);
        }

        return createProject(directory, config);
    }

    public static Project createProject(File directory, GameConfig config) throws IOException {
        if(directory.exists()) {
            throw new IllegalArgumentException("Directory " + directory.getAbsolutePath() + " already exists");
        }

        Project project = new Project();
        project.setDirectory(directory);
        project.setGameConfig(config);

        System.out.println("Creating project directory " + directory.getAbsolutePath() + " with config " + config);

        File projectConfig = new File(directory, "config.json");

        try {
            if(!directory.mkdir()) {
                throw new IOException("Failed to create file " + directory.getAbsolutePath());
            }

            // Create project directory
            config.saveToFile(projectConfig, true);

            // Create database
            project.createDatabase();

            return project;
        } catch (IOException ioe) {
            cleanup(directory);

            throw ioe;
        } catch (SQLException sqle) {
            cleanup(directory);

            throw new IOException("Failed to create project", sqle);
        } catch (RuntimeException rte) {
            cleanup(directory);

            throw rte;
        } catch (Error e) {
            cleanup(directory);

            throw e;
        }
    }

    private static void cleanup(File directory) {
        if(directory.exists()) {
            deleteAll(directory);
        }
    }

    private static void deleteAll(File directory) {
        File[] files = directory.listFiles();

        if(null != files) {
            for(File file : files) {
                if(file.isDirectory()) {
                    // Delete contents
                    deleteAll(file);
                } else {
                    // Delete file
                    if(!file.delete()) {
                        System.err.println("Failed to delete " + file.getAbsolutePath());
                    }
                }
            }
        }

        if(!directory.delete()) {
            System.err.println("Failed to delete " + directory.getAbsolutePath());
        }
    }

    /**
     * Loads a project from a project directory.
     *
     * @param dir The directory where the project resides.
     *
     * @return The loaded project.
     *
     * @throws IOException If there is a problem loading the project.
     */
    public static Project loadProject(File dir) throws IOException {
        Project project = new Project();

        project.setDirectory(dir);
        project.readConfig();
        try {
            project.loadDatabase("jdbc:derby:" + new File(dir, DB_SUBDIRECTORY).getAbsolutePath());
            project.verifyDatabase();
        } catch (SQLException sqle) {
            throw new IOException("Database error", sqle);
        }
        project.loadAnalytics();

        return project;
    }

    /**
     * Cleans-up after any database-related activity.
     *
     * This is an ugly hack to shut down the Apache Derby database engine
     * if it's been started in this JVM instance.
     *
     * @throws SQLException If there is a problem shutting-down Apache Derby.
     */
    public static void dispose() throws SQLException {
        if(_derbyLoaded) {
            System.out.println("Shutting down Derby...");

            // NOTE: This getConnection() call should throw a SQLException
            try (Connection conn = DriverManager.getConnection("jdbc:derby:;shutdown=true");) {
                // Just in case it does not for some reason, close the Connection.
            } catch (SQLException sqle) {
                if (( (sqle.getErrorCode() == 50000)
                        && ("XJ015".equals(sqle.getSQLState()) ))) {
                    // we got the expected exception
                    // System.out.println("Derby shut down normally");
                    // Note that for single database shutdown, the expected
                    // SQL state is "08006", and the error code is 45000.
                } else {
                    // if the error code or SQLState is different, we have
                    // an unexpected exception (shutdown failed)

                    throw sqle;
                }
            }

            _derbyLoaded = false;
        }
    }

    private static void usage(PrintStream out) {
        out.println("Usage: " + Project.class.getName() + " [options]");
        out.println();
        out.println("Options:");
        out.println("    -c, --config file  Specifies a config file to use. (Use with --new)");
        out.println("    -d, --directory    Specifies the project directory.");
        out.println("    -i, --info         Print project information.");
        out.println("    -n, --new          Creates a new project.");
        out.println("    -o, --output       Write output (e.g. export) to the specified file. (default stdout)");
        out.println("    -x, --export       Exports a project's database.");
        out.println("    -a, --add data     Adds a record to the project's database. (Tab-separated string)");
        out.println("    -q, --query sql    Query the database with the specified SQL query.");
        out.println("    --query-file file  Query the database with a SQL query stored in the specified file.");
    }

    private enum Operation {
        create,
        info,
        add,
        query,
        export;
    }

    public static void main(String[] args) throws Exception {
        int argindex = 0;
        File directory = null;
        File configFile = null;
        File output = null;
        String data = null;
        String query = null;
        Operation operation = null;

        while(argindex < args.length) {
            String arg = args[argindex++];

            if("--info".equals(arg) || "-i".equals(arg)) {
                operation = Operation.info;
            } else if("--export".equals(arg) || "-x".equals(arg)) {
                operation = Operation.export;
            } else if("--query".equals(arg) || "-q".equals(arg)) {
                operation = Operation.query;

                query = args[argindex++];
            } else if("--query-file".equals(arg)) {
                operation = Operation.query;

                query = readFile(args[argindex++]).trim();
            } else if("--new".equals(arg) || "-n".equals(arg)) {
                operation = Operation.create;
            } else if("--directory".equals(arg) || "-d".equals(arg)) {
                directory = new File(args[argindex++]);
            } else if("--config".equals(arg) || "-c".equals(arg)) {
                configFile = new File(args[argindex++]);
            } else if("--output".equals(arg) || "-o".equals(arg)) {
                output = new File(args[argindex++]);
            } else if("--add".equals(arg) || "-a".equals(arg)) {
                operation = Operation.add;
                data = args[argindex++];
            } else if("--help".equals(arg) || "-h".equals(arg)) {
                usage(System.out);

                System.exit(0);
            } else if ("--".equals(arg)) {
                // Last option argument

                break;
            } else {
                System.err.println("Unrecognized argument: " + arg);

                usage(System.err);

                System.exit(1);
            }
        }

        if(Operation.info == operation) {
            if(null == directory) {
                System.err.println("Must specify --directory");

                usage(System.err);

                System.exit(1);
            }
            Project project = Project.loadProject(directory);

            GameConfig config = project.getGameConfig();

            System.out.println("Project: " + config.getPageTitle());
            System.out.println();
            System.out.println("Record count: " + project.getRecordCount());
            System.out.println();
            System.out.println("Scouting fields:");
            for(Field field : config.getFields()) {
                System.out.println("  " + field.getTitle() + " / " + field.getCode());
            }
            System.out.println("Analytics: " + project.getAnalytics());
        } else if(Operation.add == operation) {
            if(null == directory) {
                System.err.println("Must specify --directory");

                usage(System.err);

                System.exit(1);
            }

            Project project = Project.loadProject(directory);

            project.insertRecord(data);
        } else if(Operation.query == operation) {
            Project project = Project.loadProject(directory);

            List<Object[]> rows = project.queryDatabase(query);

            for(Object[] row : rows) {
                for(int i=0; i<row.length; ++i) {
                    if(i > 0) System.out.print(',');

                    System.out.print(row[i]);
                }

                System.out.println();
            }
        } else if(Operation.export == operation) {
            if(null == directory) {
                System.err.println("Must specify --directory");

                usage(System.err);

                System.exit(1);
            }

            Project project = Project.loadProject(directory);

            Writer out = null;

            try {
                if(null != output) {
                    out = new OutputStreamWriter(new FileOutputStream(output), StandardCharsets.UTF_8);
                } else {
                    out = new OutputStreamWriter(System.out, StandardCharsets.UTF_8);
                }
                project.exportDatabase(out);
            } finally {
                if(null != out) try { out.close(); }
                catch (IOException ioe) { ioe.printStackTrace(); }
            }
        } else if(Operation.create == operation) {
            if(null == directory) {
                System.err.println("Must specify --directory");

                usage(System.err);

                System.exit(1);
            }

            if(null == configFile) {
                System.err.println("Must specify --config");

                usage(System.err);

                System.exit(1);
            }

            Project.createProject(directory, configFile);

            System.out.println("Created project in "+ directory.getAbsolutePath());
        } else {
            System.out.println("No operation specified");

            usage(System.out);
        }

        Project.dispose();
    }

    private static String readFile(String filename)
        throws IOException
    {
        try(BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(filename), StandardCharsets.UTF_8))) {

            StringBuilder sql = new StringBuilder();

            String line;

            while(null != (line = in.readLine())) {
                sql.append(line).append('\n');
            }

            return sql.toString();
        }
    }

    public void updateAnalytic(Analytic oldAnalytic, Analytic newAnalytic)
        throws IOException
    {
        boolean isNew;
        String filename;
        if(null == oldAnalytic || null == oldAnalytic.getFilename()) {
            // Need a new filename for this analytic
            filename = newAnalytic.getName() + ".json";
            isNew = true;
        } else {
            filename = oldAnalytic.getFilename();
            isNew = false;
        }

        File file = new File(getDirectory(), ANALYTICS_SUBDIRECTORY);
        if(!file.isDirectory()) {
           if(!file.mkdirs()) {
               throw new IOException("Failed to create directrory " + file);
           }
        }
        file = new File(file, filename);

        newAnalytic.saveToFile(file);

        // Update the in-memory set of analytics
        if(isNew) {
            analytics.add(newAnalytic);
        } else {
            oldAnalytic.setName(newAnalytic.getName());
            oldAnalytic.setQuery(newAnalytic.getQuery());
        }
    }

    public void deleteAnalytic(Analytic analytic)
        throws IOException
    {
        String filename = analytic.getFilename();
        if(null != filename) {
            File file = new File(getDirectory(), ANALYTICS_SUBDIRECTORY);
            file = new File(file, filename);

            Files.delete(file.toPath());

            // Update the in-memory set of analytics
            for(Iterator<Analytic> i = analytics.iterator(); i.hasNext(); ) {
                Analytic a = i.next();
                if(Objects.equals(a.getFilename(), analytic.getFilename())) {
                    i.remove();
                }
            }
        }
    }

    /**
     * Applies changes between the current game config and the one
     * passed-in.
     */
    public void applyChanges(GameConfig config) throws SQLException {
        // We may need to make database changes based upon the fields
        // found in the new config.
        //
        // Note that the order of the Sections and Fields is not relevant
        // to the database, nor is the inclusion of a Field in one particular
        // section or the other. We can basically ignore the Sections and
        // only look at the Fields.
        //
        // Some fields may be new and some fields may have been removed.
        // New fields aren't much different than updated ones, but removed
        // fields require a little bit of nuance because they are still in
        // the database but not in the config object any more.
        //

        String databaseURL = getDatabaseURL();

        try(Connection conn = DriverManager.getConnection(databaseURL)) {
            List<Section> sections = config.getSections();
            if(null != sections && !sections.isEmpty()) {
                // First, let's update everything we still have.
                ArrayList<Field> allFields = new ArrayList<>();

                for(Section section : sections) {
                    List<Field> fields = section.getFields();
                    if(null != fields && !fields.isEmpty()) {
                        for(Field field : fields) {
                            updateField(field, conn);

                            allFields.add(field);
                        }
                    }
                }

                // Then, remove everything we haven't updated
                removeExcessFields(allFields, conn);
            } else {
                removeAllFields(conn);
            }
        }
    }

    private void updateField(Field field, Connection conn) throws SQLException {
        // Figure out if we have to ADD or MODIFY a column
        DatabaseMetaData dbmd = conn.getMetaData();

        String columnName = normalizeColumnName(field.getCode());
        String desiredColumnType = getSQLDataType(field.getType());
        try (ResultSet rs = dbmd.getColumns(null, "APP", "STAND_SCOUTING", columnName);
             Statement stmt = conn.createStatement()) {
            if(rs.next()) {
                // Column exists; check that the type is the desired type
                String columnType = rs.getString("TYPE_NAME");
                if(columnType.contains("CHAR")) {
                    columnType += "(" + rs.getInt("COLUMN_SIZE") + ")";
                }
                if(!columnType.equals(desiredColumnType)) {
                    System.out.println("Column " + columnName + " needs to be updated from " + columnType + " to " + desiredColumnType);
                    // Apache Derby doesn't do ALTER TABLE MODIFY COLUMN
                    // We have to ADD COLUMN temp
                    // Copy old -> new
                    // DROP column
                    // RENAME temp -> column
                    conn.setAutoCommit(false); // BEGIN TRANSACTION
                    try {
                        String s = "ALTER TABLE stand_scouting ADD COLUMN temp " + desiredColumnType;
                        System.out.println("Executing statement: " + s);
                        stmt.execute(s);

                        // Copy from columnName -> temp
                        try {
                            s = "UPDATE stand_scouting SET temp=\"" + columnName + "\"";
                            System.out.println("Executing statement: " + s);
                            stmt.execute(s);
                        } catch (SQLException sqle) {
                            // Ignore this and keep going
                            System.out.println("WARNING: Failed to copy temp->" + columnName + ": " + sqle.getMessage() + "; continuing");
                        }

                        s = "ALTER TABLE stand_scouting DROP COLUMN \"" + columnName + "\"";
                        System.out.println("Executing statement: " + s);
                        stmt.execute(s);

                        s = "RENAME COLUMN stand_scouting.temp TO \"" + columnName + "\"";
                        System.out.println("Executing statement: " + s);
                        stmt.execute(s);
                    } catch (SQLException | RuntimeException | Error e) {
                        // Something went wrong and we want to undo everything
                        try { conn.rollback(); } catch (SQLException sqle) {
                            sqle.printStackTrace();
                        }
                        throw e;
                    } finally {
                        conn.setAutoCommit(true);
                    }
                } else {
                    System.out.println("Column " + columnName + " is already the right type: " + columnType);
                }
            } else {
                // Column needs to be added
                String s = "ALTER TABLE stand_scouting ADD COLUMN \"" + columnName + "\" " + desiredColumnType;
                System.out.println("Executing statement: " + s);
                stmt.execute(s);
            }
        }
    }

    private void removeAllFields(Connection conn) throws SQLException {
        removeExcessFields(Collections.emptySet(), conn);
    }

    private void removeExcessFields(Collection<Field> retainedFields, Connection conn) throws SQLException {
        if(null == retainedFields) {
            retainedFields = Collections.emptySet();
        }

        Set<String> dbFieldNames = getColumnNames("stand_scouting", conn);
        Set<String> retainedFieldNames = retainedFields.stream()
                .map(Field::getCode) // NOTE: *code* is used for the column name
                .map((s) -> normalizeColumnName(s)) // and we have to normalize it
                .collect(Collectors.toSet())
                ;

        // Remove all still-used field names (codes)
        dbFieldNames.removeAll(retainedFieldNames);
        dbFieldNames.remove("ID");
        dbFieldNames.remove("DELETED");

        if(!dbFieldNames.isEmpty()) {
            try(Statement stmt = conn.createStatement()) {
                for(String dbField : dbFieldNames) {
                    String s = "ALTER TABLE stand_scouting DROP COLUMN \"" + dbField + "\"";
                    System.out.println("Executing statement: " + s);
                    stmt.execute(s);
                }
            }
        }
    }

    private Set<String> getColumnNames(String table, Connection conn) throws SQLException {
        DatabaseMetaData dbmd = conn.getMetaData();

        HashSet<String> columnNames = new HashSet<>();
        try (ResultSet rs = dbmd.getColumns(null, "APP", "STAND_SCOUTING", null);
            Statement stmt = conn.createStatement()) {
            while(rs.next()) {
                columnNames.add(rs.getString("COLUMN_NAME"));
            }
        }

        return columnNames;
    }
}
