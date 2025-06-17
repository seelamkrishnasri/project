import java.io.*;
import java.sql.*;
import java.util.Scanner;

public class FileDBExecutor3 {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        String url = "jdbc:mysql://localhost:3306/college";
        String user = "root";
        String password = "krishna";

        System.out.println("========= SQL File Executor =========");
        System.out.println("1. CREATE TABLE commands");
        System.out.println("2. INSERT DEPT rows");
        System.out.println("3. INSERT EMP rows");
        System.out.println("4. INSERT SALGRADE rows");
        System.out.println("5. DELETE FROM EMP");
        System.out.println("6. DELETE FROM DEPT");
        System.out.println("7. DELETE FROM SALGRADE");
        System.out.print("Enter your choice: ");
        int choice = scanner.nextInt();

        String filePath = null;
        String tableName = null;

        switch (choice) {
            case 1: filePath = "C:\\595\\table1.txt"; tableName = "DEPT"; break;
            case 2: filePath = "C:\\595\\dep.txt"; tableName = "DEPT"; break;
            case 3: filePath = "C:\\595\\emp.txt"; tableName = "EMP"; break;
            case 4: filePath = "C:\\595\\salgrade.txt"; tableName = "SALGRADE"; break;
            case 5: filePath = "C:\\595\\deltemp.txt"; tableName = "EMP"; break;
            case 6: filePath = "C:\\595\\deltdep.txt"; tableName = "DEPT"; break;
            case 7: filePath = "C:\\595\\delsalgrad.txt"; tableName = "SALGRADE"; break;
            default:
                System.out.println("Invalid choice.");
                return;
        }

        try (
            Connection conn = DriverManager.getConnection(url, user, password);
            Statement stmt = conn.createStatement()
        ) {
            Class.forName("com.mysql.cj.jdbc.Driver");

            System.out.println("Connected to the database!");

            BufferedReader reader = new BufferedReader(new FileReader(filePath));
            StringBuilder queryBuilder = new StringBuilder();
            String line;

            System.out.println("Executing queries from file:\n");

            while ((line = reader.readLine()) != null) {
                line = line.trim().replace('\u00A0', ' ').replaceAll("[\\p{C}]", "");
                if (line.isEmpty()) continue;

                queryBuilder.append(line).append(" ");

                if (line.endsWith(";")) {
                    String query = queryBuilder.toString().trim();
                    query = query.substring(0, query.length() - 1); // remove ;

                    System.out.println("Query: " + query);

                    if (query.toLowerCase().startsWith("insert into")) {
                        try {
                            // Extract values inside the parentheses
                            int start = query.indexOf("VALUES(") + 7;
                            int end = query.lastIndexOf(")");
                            String valuesStr = query.substring(start, end);
                            String[] values = valuesStr.split(",", -1);
                            String keyValue = values[0].replaceAll("[^0-9]", "").trim(); // Primary key is usually first

                            // Check for duplicate before inserting
                            String pkColumn = tableName.equals("DEPT") ? "DEPTNO" :
                                              tableName.equals("EMP") ? "EMPNO" :
                                              tableName.equals("SALGRADE") ? "GRADE" : null;

                            if (pkColumn != null && !keyValue.isEmpty()) {
                                String checkQuery = "SELECT * FROM " + tableName + " WHERE " + pkColumn + " = " + keyValue;
                                ResultSet rs = stmt.executeQuery(checkQuery);
                                if (rs.next()) {
                                    System.out.println("Duplicate entry found in " + tableName + " for " + pkColumn + " = " + keyValue);
                                    ResultSetMetaData rsmd = rs.getMetaData();
                                    int cols = rsmd.getColumnCount();
                                    for (int i = 1; i <= cols; i++) {
                                        System.out.print(rs.getString(i) + "\t");
                                    }
                                    System.out.println();
                                    rs.close();
                                } else {
                                    int rowsAffected = stmt.executeUpdate(query);
                                    System.out.println("Query OK, " + rowsAffected + " row(s) affected.");
                                }
                            } else {
                                int rowsAffected = stmt.executeUpdate(query);
                                System.out.println("Query OK, " + rowsAffected + " row(s) affected.");
                            }
                        } catch (SQLException e) {
                            System.err.println("SQL Error during insert: " + e.getMessage());
                        }
                    } else {
                        // Non-insert queries (e.g., CREATE or DELETE)
                        try {
                            int rowsAffected = stmt.executeUpdate(query);
                            System.out.println("Query OK, " + rowsAffected + " row(s) affected.");
                        } catch (SQLException e) {
                            System.err.println("SQL Error: " + e.getMessage());
                        }
                    }

                    System.out.println("--------------------------------------------------");
                    queryBuilder.setLength(0);
                }
            }

            reader.close();

        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC Driver not found.");
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("File read error: " + e.getMessage());
        }
    }
}