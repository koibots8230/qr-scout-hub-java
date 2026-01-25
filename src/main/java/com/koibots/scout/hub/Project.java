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
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.koibots.scout.hub.GameConfig.Field;
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
        StringBuilder sql = new StringBuilder("CREATE TABLE stand_scouting (id INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1)");

        for(GameConfig.Field field : config.getFields()) {
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
        try(PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) AS cnt FROM stand_scouting");
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

        try(Connection conn = DriverManager.getConnection(databaseURL)) {

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

                // Check all columns are defined
                for(Field field : config.getFields()) {
                    String columnName = normalizeColumnName(field.getCode());

                    if(!dbFields.containsKey(columnName)) {
                        throw new IllegalStateException("Config contains field not found in database: " + field.getCode() + " / " + columnName);
                    }
                }

                for(Map.Entry<String,String> entry : dbFields.entrySet()) {
                    String columnName = entry.getKey();

                    if(!"id".equalsIgnoreCase(columnName)) {
                        Field field = getFieldFromSQLColumn(config, columnName);
                        if(null == field) {
                            throw new IllegalStateException("Database contains field not found in configuration: " + columnName);
                        }
                    }
                }

                System.out.println("Verification complete");
            } finally {
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
            throw new IllegalArgumentException("Code and game config size mismatch: " + fields.size() + " != " + values.length);
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
        StringBuilder select = new StringBuilder("SELECT id, ");

        Collection<Field> fields = getGameConfig().getFields();

        boolean first = true;
        for(Field field : fields) {
            if(first) { first = false; }
            else { select.append(','); }

            select.append('"').append(normalizeColumnName(field.getCode())).append('"');
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

        boolean first = true;
        for(Field field : fields) {
            if(first) { first = false; }
            else { sql.append(','); }

            sql.append('"').append(normalizeColumnName(field.getCode())).append('"');
        }
        sql.append(" FROM stand_scouting");

        try (Connection conn = DriverManager.getConnection(getDatabaseURL());
             PreparedStatement ps = conn.prepareStatement(sql.toString());
             ResultSet rs = ps.executeQuery()) {

            ResultSetMetaData rsmd = rs.getMetaData();

            try (CSVWriter csv = new CSVWriter(out)) {
                String[] data = new String[rsmd.getColumnCount()];
                first = true;
                for(int i=0; i < rsmd.getColumnCount(); ++i) {
                    Field field = getFieldFromSQLColumn(config, rsmd.getColumnName(i+1));

                    data[i] = field.getTitle();
                }
                csv.writeNext(data);

                while(rs.next()) {
                    first = true;
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

    public List<Object[]> queryDatabase(String sql)
        throws IOException, SQLException
    {
        System.out.println("Running query: " + sql);

        try (Connection conn = DriverManager.getConnection(getDatabaseURL());
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

               ResultSetMetaData rsmd = rs.getMetaData();

               ArrayList<Object[]> rows = new ArrayList<>();

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
        if(directory.exists()) {
            throw new IllegalArgumentException("Directory " + directory.getAbsolutePath() + " already exists");
        }

        Project project = new Project();
        project.setDirectory(directory);

        try {
            GameConfig config = GameConfig.readFile(configFile);

            project.setGameConfig(config);

            System.out.println("Creating project with config: " + config);

            // Create project directory
            if(!directory.mkdir()) {
                throw new IOException("Failed to create file " + directory.getAbsolutePath());
            }

            File projectConfig = new File(directory, "config.json");

            // Copy config file in to project
            Files.copy(configFile.toPath(), projectConfig.toPath(), StandardCopyOption.REPLACE_EXISTING);

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
        String filename = oldAnalytic.getFilename();
        if(null == oldAnalytic.getFilename()) {
            // Need a new filename for this analytic
            filename = newAnalytic.getName() + ".json";
        }
        File file = new File(getDirectory(), ANALYTICS_SUBDIRECTORY);
        file = new File(file, filename);

        newAnalytic.saveToFile(file);
    }
}
