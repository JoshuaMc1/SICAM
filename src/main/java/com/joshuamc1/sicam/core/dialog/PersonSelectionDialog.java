package com.joshuamc1.sicam.core.dialog;

import com.joshuamc1.sicam.models.Person;
import com.joshuamc1.sicam.services.PersonService;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.util.List;

import com.joshuamc1.sicam.core.Logs;

public class PersonSelectionDialog {

    public static class PersonSelection {
        private final int personId;
        private final String personName;

        public PersonSelection(int personId, String personName) {
            this.personId = personId;
            this.personName = personName;
        }

        public int getPersonId() {
            return personId;
        }

        public String getPersonName() {
            return personName;
        }
    }

    public static PersonSelection showDialog(String title) {
        Dialog<PersonSelection> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText("Seleccione la persona");

        ButtonType selectButtonType = new ButtonType("Seleccionar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(selectButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        // Campo de búsqueda
        javafx.scene.control.TextField searchField = new javafx.scene.control.TextField();
        searchField.setPromptText("Buscar por DNI o nombre...");
        searchField.setPrefWidth(250);

        ComboBox<Person> personCombo = new ComboBox<>();
        personCombo.setPromptText("Seleccione persona");
        personCombo.setPrefWidth(250);

        // Cargar personas
        List<Person> allPeople = new java.util.ArrayList<>();
        try {
            PersonService personService = new PersonService();
            allPeople.addAll(personService.listPaged(0, 1000, null));
            personCombo.getItems().addAll(allPeople);

            // Configurar cómo se muestra cada persona
            personCombo.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(Person person, boolean empty) {
                    super.updateItem(person, empty);
                    if (empty || person == null) {
                        setText(null);
                    } else {
                        String name = person.getString("first_name") + " " + person.getString("last_name");
                        String dni = person.getString("dni");
                        setText(name + " (DNI: " + dni + ")");
                    }
                }
            });

            personCombo.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(Person person, boolean empty) {
                    super.updateItem(person, empty);
                    if (empty || person == null) {
                        setText(null);
                    } else {
                        String name = person.getString("first_name") + " " + person.getString("last_name");
                        String dni = person.getString("dni");
                        setText(name + " (DNI: " + dni + ")");
                    }
                }
            });

            if (!allPeople.isEmpty()) {
                personCombo.getSelectionModel().selectFirst();
            }
        } catch (Exception e) {
            Logs.error(PersonSelectionDialog.class, "Error cargando personas: {}", e.getMessage());
        }

        // Filtrado en tiempo real
        final List<Person> finalAllPeople = allPeople;
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            String filter = newValue.toLowerCase().trim();
            personCombo.getItems().clear();

            if (filter.isEmpty()) {
                personCombo.getItems().addAll(finalAllPeople);
            } else {
                for (Person person : finalAllPeople) {
                    String dni = person.getString("dni") != null ? person.getString("dni").toLowerCase() : "";
                    String firstName = person.getString("first_name") != null ? person.getString("first_name").toLowerCase() : "";
                    String lastName = person.getString("last_name") != null ? person.getString("last_name").toLowerCase() : "";
                    String fullName = (firstName + " " + lastName).trim();

                    if (dni.contains(filter) || firstName.contains(filter) ||
                        lastName.contains(filter) || fullName.contains(filter)) {
                        personCombo.getItems().add(person);
                    }
                }
            }

            if (!personCombo.getItems().isEmpty()) {
                personCombo.getSelectionModel().selectFirst();
            }
        });

        Label lblSearch = new Label("Buscar:");
        lblSearch.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");

        Label lblPerson = new Label("Persona:");
        lblPerson.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");

        grid.add(lblSearch, 0, 0);
        grid.add(searchField, 1, 0);
        grid.add(lblPerson, 0, 1);
        grid.add(personCombo, 1, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == selectButtonType) {
                Person selectedPerson = personCombo.getValue();
                if (selectedPerson != null) {
                    String fullName = selectedPerson.getString("first_name") + " " +
                                    selectedPerson.getString("last_name");
                    return new PersonSelection(
                        selectedPerson.getInteger("id"),
                        fullName
                    );
                }
            }
            return null;
        });

        return dialog.showAndWait().orElse(null);
    }
}
