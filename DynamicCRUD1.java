import javafx.application.Application;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.sql.*;
import java.util.*;

public class DynamicCRUD1 extends Application {
    private ComboBox<String> tableComboBox = new ComboBox<>();
    private TableView<Map<String, Object>> tableView = new TableView<>();
    private VBox formBox = new VBox(10);
    private final Map<String, TextField> fieldInputs = new HashMap<>();

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/college", "root", "krishna");
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("Dynamic CRUD UI - JavaFX & MySQL");

        Label selectLabel = new Label("Select Table:");
        Button loadBtn = new Button("Load Table");
        Button createBtn = new Button("Create Table");
        Button dropBtn = new Button("Drop Table");
        Button modifyBtn = new Button("Modify Table");

        HBox topBar = new HBox(10, selectLabel, tableComboBox, loadBtn, createBtn, dropBtn, modifyBtn);
        topBar.setPadding(new Insets(10));

        HBox buttons = new HBox(10);
        Button insertBtn = new Button("Insert");
        Button updateBtn = new Button("Update");
        Button deleteBtn = new Button("Delete");
        buttons.getChildren().addAll(insertBtn, updateBtn, deleteBtn);

        VBox layout = new VBox(10, topBar, tableView, new Label("Fields:"), formBox, buttons);
        layout.setPadding(new Insets(15));

        loadDefinedTables();

        loadBtn.setOnAction(e -> loadSelectedTable());
        insertBtn.setOnAction(e -> insertData());
        updateBtn.setOnAction(e -> updateData());
        deleteBtn.setOnAction(e -> deleteData());
        createBtn.setOnAction(e -> createNewTable());
        dropBtn.setOnAction(e -> dropSelectedTable());
        modifyBtn.setOnAction(e -> modifyTableStructure());

        Scene scene = new Scene(layout, 800, 600);
        stage.setScene(scene);
        stage.show();
    }

    private void loadDefinedTables() {
        try (Connection conn = getConnection();
             ResultSet rs = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            List<String> tables = new ArrayList<>();
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
            tableComboBox.getItems().setAll(tables);
        } catch (Exception e) {
            showAlert("Error loading tables: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void loadSelectedTable() {
        String tableName = tableComboBox.getValue();
        if (tableName == null || tableName.isEmpty()) {
            showAlert("Invalid table selected.", Alert.AlertType.ERROR);
            return;
        }

        tableView.getColumns().clear();
        tableView.getItems().clear();
        formBox.getChildren().clear();
        fieldInputs.clear();

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName + " LIMIT 1")) {

            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            for (int i = 1; i <= colCount; i++) {
                String colName = meta.getColumnName(i);
                TableColumn<Map<String, Object>, Object> column = new TableColumn<>(colName);
                column.setCellValueFactory(cellData ->
                        new SimpleObjectProperty<>(cellData.getValue().get(colName)));
                tableView.getColumns().add(column);

                TextField field = new TextField();
                field.setPromptText(colName);
                fieldInputs.put(colName, field);
                formBox.getChildren().add(field);
            }

        } catch (Exception e) {
            showAlert("Error loading metadata: " + e.getMessage(), Alert.AlertType.ERROR);
        }

        refreshTableData();
    }

    private void refreshTableData() {
        String tableName = tableComboBox.getValue();
        if (tableName == null || tableName.isEmpty()) return;

        ObservableList<Map<String, Object>> data = FXCollections.observableArrayList();

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName)) {

            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    row.put(meta.getColumnName(i), rs.getObject(i));
                }
                data.add(row);
            }
            tableView.setItems(data);

        } catch (Exception e) {
            showAlert("Error refreshing data: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void insertData() {
        String table = tableComboBox.getValue();
        if (table == null || table.isEmpty()) return;

        try (Connection conn = getConnection()) {
            StringBuilder cols = new StringBuilder();
            StringBuilder vals = new StringBuilder();
            List<String> values = new ArrayList<>();

            for (Map.Entry<String, TextField> entry : fieldInputs.entrySet()) {
                String col = entry.getKey();
                String val = entry.getValue().getText();
                if (!val.isEmpty()) {
                    cols.append(col).append(",");
                    vals.append("?,");
                    values.add(val);
                }
            }

            if (values.isEmpty()) {
                showAlert("Please fill in at least one field.", Alert.AlertType.WARNING);
                return;
            }

            String sql = "INSERT INTO " + table + " (" +
                    cols.substring(0, cols.length() - 1) + ") VALUES (" +
                    vals.substring(0, vals.length() - 1) + ")";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            for (int i = 0; i < values.size(); i++) {
                pstmt.setString(i + 1, values.get(i));
            }
            pstmt.executeUpdate();

            showAlert("Record inserted successfully!", Alert.AlertType.INFORMATION);
            refreshTableData();

        } catch (Exception e) {
            showAlert("Insert Error: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void updateData() {
        String table = tableComboBox.getValue();
        if (table == null || table.isEmpty()) return;

        Map<String, Object> selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Please select a row to update.", Alert.AlertType.WARNING);
            return;
        }

        try (Connection conn = getConnection()) {
            StringBuilder setClause = new StringBuilder();
            List<String> values = new ArrayList<>();

            for (Map.Entry<String, TextField> entry : fieldInputs.entrySet()) {
                String col = entry.getKey();
                String val = entry.getValue().getText();
                if (!val.isEmpty()) {
                    setClause.append(col).append("=?,");
                    values.add(val);
                }
            }

            if (values.isEmpty()) {
                showAlert("No changes to update.", Alert.AlertType.WARNING);
                return;
            }

            String keyCol = tableView.getColumns().get(0).getText();
            Object keyVal = selected.get(keyCol);
            values.add(keyVal.toString());

            String sql = "UPDATE " + table + " SET " +
                    setClause.substring(0, setClause.length() - 1) +
                    " WHERE " + keyCol + "=?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            for (int i = 0; i < values.size(); i++) {
                pstmt.setString(i + 1, values.get(i));
            }
            pstmt.executeUpdate();

            showAlert("Record updated successfully!", Alert.AlertType.INFORMATION);
            refreshTableData();

        } catch (Exception e) {
            showAlert("Update Error: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void deleteData() {
        String table = tableComboBox.getValue();
        if (table == null || table.isEmpty()) return;

        Map<String, Object> selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Please select a row to delete.", Alert.AlertType.WARNING);
            return;
        }

        try (Connection conn = getConnection()) {
            String keyCol = tableView.getColumns().get(0).getText();
            Object keyVal = selected.get(keyCol);

            String sql = "DELETE FROM " + table + " WHERE " + keyCol + "=?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setObject(1, keyVal);
            pstmt.executeUpdate();

            showAlert("Record deleted successfully!", Alert.AlertType.INFORMATION);
            refreshTableData();

        } catch (Exception e) {
            showAlert("Delete Error: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void dropSelectedTable() {
        String table = tableComboBox.getValue();
        if (table == null || table.isEmpty()) {
            showAlert("Please select a table to drop.", Alert.AlertType.WARNING);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to drop the table '" + table + "'?", ButtonType.YES, ButtonType.NO);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {

                stmt.executeUpdate("DROP TABLE " + table);
                showAlert("Table '" + table + "' dropped successfully!", Alert.AlertType.INFORMATION);
                loadDefinedTables();
                tableView.getItems().clear();
                tableView.getColumns().clear();
                formBox.getChildren().clear();
                fieldInputs.clear();
            } catch (Exception e) {
                showAlert("Drop Error: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    private void modifyTableStructure() {
        String table = tableComboBox.getValue();
        if (table == null || table.isEmpty()) {
            showAlert("Please select a table to modify.", Alert.AlertType.WARNING);
            return;
        }

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Modify Table Structure");

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));

        Label info = new Label("Enter ALTER TABLE SQL command (e.g., ADD COLUMN age INT):");
        TextArea commandArea = new TextArea();
        commandArea.setPromptText("ADD COLUMN age INT");

        vbox.getChildren().addAll(info, commandArea);
        dialog.getDialogPane().setContent(vbox);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                return commandArea.getText().trim();
            }
            return null;
        });

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(command -> {
            if (command.isEmpty()) {
                showAlert("Command cannot be empty.", Alert.AlertType.WARNING);
                return;
            }

            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {

                String sql = "ALTER TABLE " + table + " " + command;
                stmt.executeUpdate(sql);

                showAlert("Table '" + table + "' modified successfully!", Alert.AlertType.INFORMATION);
                loadSelectedTable();

            } catch (Exception e) {
                showAlert("Modify Error: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        });
    }

    private void createNewTable() {
        Dialog<Map<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Create New Table");

        VBox vbox = new VBox(10);
        TextField tableNameField = new TextField();
        tableNameField.setPromptText("Table Name");

        TextArea columnsField = new TextArea();
        columnsField.setPromptText("Column Definitions (e.g., id INT PRIMARY KEY, name VARCHAR(50))");

        vbox.getChildren().addAll(new Label("Table Name:"), tableNameField, new Label("Columns:"), columnsField);
        dialog.getDialogPane().setContent(vbox);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                Map<String, String> result = new HashMap<>();
                result.put("table", tableNameField.getText().trim());
                result.put("columns", columnsField.getText().trim());
                return result;
            }
            return null;
        });

        Optional<Map<String, String>> result = dialog.showAndWait();
        result.ifPresent(data -> {
            String table = data.get("table");
            String columns = data.get("columns");

            if (table.isEmpty() || columns.isEmpty()) {
                showAlert("Table name and columns must not be empty.", Alert.AlertType.WARNING);
                return;
            }

            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {

                String sql = "CREATE TABLE " + table + " (" + columns + ")";
                stmt.executeUpdate(sql);
                showAlert("Table '" + table + "' created successfully!", Alert.AlertType.INFORMATION);
                loadDefinedTables();

            } catch (Exception e) {
                showAlert("Create Error: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        });
    }

    private void showAlert(String msg, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
