package com.joshuamc1.sicam.core.dialog;

import com.joshuamc1.sicam.core.Logs;
import com.joshuamc1.sicam.models.User;
import com.joshuamc1.sicam.services.UserService;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.util.List;

public class UserSelectionDialog {

    public static class UserSelection {
        private final int userId;
        private final String userName;

        public UserSelection(int userId, String userName) {
            this.userId = userId;
            this.userName = userName;
        }

        public int getUserId() {
            return userId;
        }

        public String getUserName() {
            return userName;
        }
    }

    public static UserSelection showDialog(String title) {
        Dialog<UserSelection> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText("Seleccione el usuario");

        ButtonType selectButtonType = new ButtonType("Seleccionar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(selectButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        // Campo de búsqueda
        javafx.scene.control.TextField searchField = new javafx.scene.control.TextField();
        searchField.setPromptText("Buscar por username o nombre...");
        searchField.setPrefWidth(250);

        ComboBox<User> userCombo = new ComboBox<>();
        userCombo.setPromptText("Seleccione usuario");
        userCombo.setPrefWidth(250);

        // Cargar usuarios
        List<User> allUsers = new java.util.ArrayList<>();
        try {
            UserService userService = new UserService();
            allUsers.addAll(userService.listPaged(0, 1000, null));
            userCombo.getItems().addAll(allUsers);

            // Configurar cómo se muestra cada usuario
            userCombo.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(User user, boolean empty) {
                    super.updateItem(user, empty);
                    if (empty || user == null) {
                        setText(null);
                    } else {
                        setText(user.getString("name") + " (@" + user.getString("username") + ")");
                    }
                }
            });

            userCombo.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(User user, boolean empty) {
                    super.updateItem(user, empty);
                    if (empty || user == null) {
                        setText(null);
                    } else {
                        setText(user.getString("name") + " (@" + user.getString("username") + ")");
                    }
                }
            });

            if (!allUsers.isEmpty()) {
                userCombo.getSelectionModel().selectFirst();
            }
        } catch (Exception e) {
            Logs.error(UserSelectionDialog.class, "Error cargando usuarios: {}", e.getMessage());
        }

        // Filtrado en tiempo real
        final List<User> finalAllUsers = allUsers;
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            String filter = newValue.toLowerCase().trim();
            userCombo.getItems().clear();

            if (filter.isEmpty()) {
                userCombo.getItems().addAll(finalAllUsers);
            } else {
                for (User user : finalAllUsers) {
                    String username = user.getString("username") != null ? user.getString("username").toLowerCase() : "";
                    String name = user.getString("name") != null ? user.getString("name").toLowerCase() : "";

                    if (username.contains(filter) || name.contains(filter)) {
                        userCombo.getItems().add(user);
                    }
                }
            }

            if (!userCombo.getItems().isEmpty()) {
                userCombo.getSelectionModel().selectFirst();
            }
        });

        Label lblSearch = new Label("Buscar:");
        lblSearch.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");

        Label lblUser = new Label("Usuario:");
        lblUser.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");

        grid.add(lblSearch, 0, 0);
        grid.add(searchField, 1, 0);
        grid.add(lblUser, 0, 1);
        grid.add(userCombo, 1, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == selectButtonType) {
                User selectedUser = userCombo.getValue();
                if (selectedUser != null) {
                    return new UserSelection(
                        selectedUser.getInteger("id"),
                        selectedUser.getString("name")
                    );
                }
            }
            return null;
        });

        return dialog.showAndWait().orElse(null);
    }
}

