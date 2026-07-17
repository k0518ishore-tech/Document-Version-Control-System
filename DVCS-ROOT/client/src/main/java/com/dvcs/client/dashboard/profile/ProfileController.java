package com.dvcs.client.dashboard.profile;

import com.dvcs.client.auth.db.MongoConnection;
import com.dvcs.client.auth.repo.UserRepository;
import com.dvcs.client.auth.service.UserService;
import com.dvcs.client.controller.LoginSignupController;
import com.dvcs.client.dashboard.MainLayoutController;
import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.bson.types.ObjectId;

public final class ProfileController {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault());

    // Sidebar
    @FXML private VBox sidebarNav;
    @FXML private Label sidebarInitialsLabel;
    @FXML private Label sidebarUserNameLabel;

    // Topbar
    @FXML private TextField topSearchField;

    // Header card
    @FXML private Label avatarInitialsLabel;
    @FXML private Label nameValueLabel;
    @FXML private Label usernameValueLabel;
    @FXML private Label bioLabel;

    // Stats
    @FXML private Label totalWorkspacesValueLabel;
    @FXML private Label totalFilesValueLabel;
    @FXML private Label totalCommitsValueLabel;
    @FXML private Label storageValueLabel;

    // Heatmap + workspaces
    @FXML private GridPane activityGrid;
    @FXML private GridPane popularWorkspaceGrid;
    @FXML private VBox popularWorkspaceEmptyBox;
    @FXML private Label popularWorkspaceEmptyLabel;

    // Recent activity
    @FXML private VBox activityFeed;
    @FXML private ScrollPane activityFeedScrollPane;

    // Edit modal
    @FXML private StackPane editProfileModal;
    @FXML private TextField editNameField;
    @FXML private TextField editUsernameField;
    @FXML private TextArea editBioField;
    @FXML private VBox passwordFieldsBox;
    @FXML private Button togglePasswordButton;
    @FXML private PasswordField passwordPopupOldField;
    @FXML private PasswordField passwordPopupNewField;
    @FXML private PasswordField passwordPopupConfirmField;

    private ProfileService profileService;
    private ObjectId currentUserId;
    private Runnable onSearchRequested;
    private Runnable onNotificationRequested;
    private Runnable onProfileRequested;
    private Runnable onCollaboratorsRequested;

    private ProfileService.ProfileViewModel currentProfile;

    @FXML
    private void initialize() {
        buildSidebarNav();
        setEditMode(false);
    }

    private Runnable onHomeRequested;

    public void configure(
            ProfileService profileService,
            ObjectId currentUserId,
            Runnable onHomeRequested,
            Runnable onSearchRequested,
            Runnable onNotificationRequested,
            Runnable onProfileRequested,
            Runnable onCollaboratorsRequested) {
        this.profileService = Objects.requireNonNull(profileService, "profileService");
        this.currentUserId = Objects.requireNonNull(currentUserId, "currentUserId");
        this.onHomeRequested = onHomeRequested;
        this.onSearchRequested = onSearchRequested;
        this.onNotificationRequested = onNotificationRequested;
        this.onProfileRequested = onProfileRequested;
        this.onCollaboratorsRequested = onCollaboratorsRequested;

        if (topSearchField != null) {
            topSearchField.setOnAction(e -> { if (onSearchRequested != null) onSearchRequested.run(); });
        }

        reloadProfile();
    }

    // ── FXML handlers ─────────────────────────────────────────────────────

    @FXML
    private void onRefreshClick(MouseEvent event) {
        reloadProfile();
    }

    @FXML
    private void onTopNotifClick(MouseEvent event) {

        if (onNotificationRequested != null) onNotificationRequested.run();
    }

    @FXML
    private void onTopProfileClick(MouseEvent event) {
        // already on profile – no-op
    }

    @FXML
    private void onEditProfile() {
        if (currentProfile == null) return;
        if (editNameField != null) editNameField.setText(currentProfile.name() == null ? "" : currentProfile.name());
        if (editUsernameField != null) editUsernameField.setText(currentProfile.username());
        if (editBioField != null) editBioField.setText(currentProfile.bio() == null ? "" : currentProfile.bio());
        if (passwordFieldsBox != null) {
            passwordFieldsBox.setVisible(false);
            passwordFieldsBox.setManaged(false);
        }
        if (togglePasswordButton != null) togglePasswordButton.setText("Change Password");
        clearPasswordFields();
        setEditMode(true);
    }

    @FXML
    private void onCancelEdit() {
        setEditMode(false);
    }

    @FXML
    private void onSaveProfile() {
        if (profileService == null || currentUserId == null) return;

        String bio = editBioField != null ? editBioField.getText() : null;
        ProfileService.UpdateResult basicResult = profileService.updateProfile(
                currentUserId, editNameField.getText(), editUsernameField.getText(), bio);
        if (!basicResult.success()) { showError(basicResult.message()); return; }

        if (passwordFieldsBox != null && passwordFieldsBox.isVisible()) {
            ProfileService.UpdateResult passResult = profileService.changePassword(
                    currentUserId,
                    passwordPopupOldField.getText(),
                    passwordPopupNewField.getText(),
                    passwordPopupConfirmField.getText());
            if (!passResult.success()) { showError(passResult.message()); return; }
        }

        showInfo("Profile updated successfully");
        reloadProfile();
    }

    @FXML
    private void onTogglePasswordFields() {
        if (passwordFieldsBox == null) return;
        boolean vis = passwordFieldsBox.isVisible();
        passwordFieldsBox.setVisible(!vis);
        passwordFieldsBox.setManaged(!vis);
        if (togglePasswordButton != null)
            togglePasswordButton.setText(vis ? "Change Password" : "Hide Password Section");
        if (vis) clearPasswordFields();
    }

    @FXML
    private void onLogout() {
        Stage profileStage = resolveStage();
        if (profileStage == null) { showError("Logout failed. Please try again."); return; }
        Stage targetStage = (profileStage.getOwner() instanceof Stage os) ? os : profileStage;
        try {
            showLandingPageOnStage(targetStage);
            if (targetStage != profileStage) profileStage.close();
        } catch (Exception e) {
            showError("Logout failed. Please try again.");
        }
    }

    @FXML
    private void onFabClick() {
        showInfo("New workspace creation coming soon.");
    }

    // ── Sidebar ───────────────────────────────────────────────────────────

    private void buildSidebarNav() {
        if (sidebarNav == null) return;
        sidebarNav.getChildren().clear();

        String[][] items = {
            {"🏠", "Home"},
            {"⊞", "Workspaces"},
            {"🔔", "Notifications"},
            {"👥", "Collaborators"},
            {"⚙", "Settings"}
        };
        boolean[] active   = {false, false, false, false, true};
        boolean[] disabled = {false, false, false, false, false};

        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            HBox item = buildNavItem(items[i][0], items[i][1], active[i], disabled[i]);
            if (!disabled[i]) item.setOnMouseClicked(e -> onNavItemClicked(idx));
            sidebarNav.getChildren().add(item);
        }
    }

    private HBox buildNavItem(String icon, String label, boolean isActive, boolean isDisabled) {
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("prof-nav-item-icon");

        Label textLabel = new Label(label);
        textLabel.getStyleClass().add("prof-nav-item-text");

        HBox item = new HBox(10, iconLabel, textLabel);
        item.getStyleClass().add("prof-nav-item");
        item.setAlignment(Pos.CENTER_LEFT);
        item.setPadding(new Insets(10, 16, 10, 16));
        item.setMaxWidth(Double.MAX_VALUE);

        if (isActive)   item.getStyleClass().add("prof-nav-active");
        if (isDisabled) item.getStyleClass().add("prof-nav-disabled");
        return item;
    }

    private void onNavItemClicked(int index) {
        switch (index) {
            case 0, 1 -> closeCurrentWindow();
            case 2 -> { if (onNotificationRequested != null) onNotificationRequested.run(); }
            case 3 -> { if (onCollaboratorsRequested != null) onCollaboratorsRequested.run(); }
            case 4 -> { /* profile is "settings"-ish – already here */ }
            default -> { }
        }
    }

    // ── Data rendering ────────────────────────────────────────────────────

    private void reloadProfile() {
        if (profileService == null || currentUserId == null) return;
        currentProfile = profileService.loadProfile(currentUserId);
        renderProfile(currentProfile);
        setEditMode(false);
    }

    private void renderProfile(ProfileService.ProfileViewModel p) {
        if (p == null) return;

        String name = p.name() == null || p.name().isBlank() ? "Not set" : p.name();
        if (avatarInitialsLabel != null)
            avatarInitialsLabel.setText(p.initials());
        if (nameValueLabel != null)     nameValueLabel.setText(name);
        if (usernameValueLabel != null) usernameValueLabel.setText("@" + p.username());
        if (bioLabel != null)           bioLabel.setText(p.bio() == null ? "" : p.bio());

        if (sidebarUserNameLabel != null) sidebarUserNameLabel.setText(p.username());
        if (sidebarInitialsLabel != null) sidebarInitialsLabel.setText(p.initials());

        if (totalWorkspacesValueLabel != null) totalWorkspacesValueLabel.setText(String.valueOf(p.totalWorkspaces()));
        if (totalFilesValueLabel != null)      totalFilesValueLabel.setText(String.valueOf(p.totalFiles()));
        if (totalCommitsValueLabel != null)    totalCommitsValueLabel.setText(String.valueOf(p.totalCommits()));
        if (storageValueLabel != null) {
            storageValueLabel.setText(p.storageQuota() != null
                    ? formatStorage(p.storageQuota()) : "N/A");
        }

        renderPopularWorkspaces(p.popularWorkspaces());
        renderActivity(p.activityDays());
        renderRecentActivity(p.recentCommits());
    }

    private void renderPopularWorkspaces(List<ProfileService.PopularWorkspace> workspaces) {
        if (popularWorkspaceGrid == null) return;
        popularWorkspaceGrid.getChildren().clear();

        List<ProfileService.PopularWorkspace> list = workspaces == null ? List.of() : workspaces;
        if (list.isEmpty()) {
            if (popularWorkspaceEmptyBox != null) {
                popularWorkspaceEmptyBox.setVisible(true);
                popularWorkspaceEmptyBox.setManaged(true);
            }
            return;
        }
        if (popularWorkspaceEmptyBox != null) {
            popularWorkspaceEmptyBox.setVisible(false);
            popularWorkspaceEmptyBox.setManaged(false);
        }

        int index = 0;
        for (ProfileService.PopularWorkspace ws : list) {
            if (index >= 6) break;
            VBox card = new VBox(4);
            card.getStyleClass().add("profile-workspace-item");
            Label nameLabel = new Label(ws.workspaceName());
            nameLabel.getStyleClass().add("profile-workspace-name");
            Label commitLabel = new Label(ws.commitCount() + " commits");
            commitLabel.getStyleClass().add("profile-workspace-commits");
            card.getChildren().addAll(nameLabel, commitLabel);
            popularWorkspaceGrid.add(card, index % 2, index / 2);
            index++;
        }
    }

    private void renderActivity(List<ProfileService.DayCommit> days) {
        if (activityGrid == null || days == null) return;
        activityGrid.getChildren().clear();

        int columns = 10;
        int index = 0;
        for (ProfileService.DayCommit day : days) {
            Region cell = new Region();
            cell.getStyleClass().addAll("profile-activity-cell", colorFor(day.commitCount()));
            cell.setPrefSize(16, 16);
            cell.setMinSize(14, 14);
            activityGrid.add(cell, index % columns, index / columns);
            index++;
        }
    }

    private void renderRecentActivity(List<ProfileService.RecentCommit> commits) {
        if (activityFeed == null) return;
        activityFeed.getChildren().clear();

        if (commits == null || commits.isEmpty()) {
            Label empty = new Label("No recent commits");
            empty.getStyleClass().add("prof-activity-sub");
            empty.setPadding(new Insets(12, 16, 12, 16));
            activityFeed.getChildren().add(empty);
            return;
        }

        for (ProfileService.RecentCommit commit : commits) {
            Label iconLabel = new Label("⑂");
            iconLabel.getStyleClass().add("prof-activity-icon");

            Label msgLabel = new Label(commit.message().isBlank() ? "(no message)" : commit.message());
            msgLabel.getStyleClass().add("prof-activity-msg");
            msgLabel.setWrapText(true);
            HBox.setHgrow(msgLabel, Priority.ALWAYS);

            String sub = commit.workspaceName()
                    + (commit.time() != null ? " • " + formatRelative(commit.time()) : "");
            Label subLabel = new Label(sub);
            subLabel.getStyleClass().add("prof-activity-sub");

            VBox textBox = new VBox(2, msgLabel, subLabel);
            HBox.setHgrow(textBox, Priority.ALWAYS);

            HBox item = new HBox(10, iconLabel, textBox);
            item.getStyleClass().add("prof-activity-item");
            item.setPadding(new Insets(10, 16, 10, 16));
            activityFeed.getChildren().add(item);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void setEditMode(boolean editing) {
        if (editProfileModal != null) {
            editProfileModal.setVisible(editing);
            editProfileModal.setManaged(editing);
        }
    }

    private void clearPasswordFields() {
        if (passwordPopupOldField != null)     passwordPopupOldField.clear();
        if (passwordPopupNewField != null)     passwordPopupNewField.clear();
        if (passwordPopupConfirmField != null) passwordPopupConfirmField.clear();
    }

    private Stage resolveStage() {
        for (Window w : Window.getWindows()) {
            if (w.isFocused() && w instanceof Stage s) return s;
        }
        return null;
    }

    private void closeCurrentWindow() {
        if (onHomeRequested != null) {
            onHomeRequested.run();
        }
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setHeaderText(null);
        Stage s = resolveStage();
        if (s != null) { alert.initOwner(s); alert.initModality(Modality.WINDOW_MODAL); }
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText("Error");
        Stage s = resolveStage();
        if (s != null) { alert.initOwner(s); alert.initModality(Modality.WINDOW_MODAL); }
        alert.showAndWait();
    }

    private static String colorFor(int commitCount) {
        if (commitCount <= 0) return "activity-glow-none";
        if (commitCount < 3) return "activity-glow-low";
        if (commitCount < 7) return "activity-glow-med";
        return "activity-glow-high";
    }

    private static String formatRelative(Instant t) {
        if (t == null) return "";
        long s = Instant.now().getEpochSecond() - t.getEpochSecond();
        if (s < 60) return "Just now";
        if (s < 3600) return (s / 60) + "m ago";
        if (s < 86400) return (s / 3600) + "h ago";
        return (s / 86400) + "d ago";
    }

    private static String formatStorage(long mb) {
        if (mb >= 1024) return String.format("%.1f GB", mb / 1024.0);
        return mb + " MB";
    }

    private void showLandingPageOnStage(Stage stage) throws Exception {
        URL fxmlUrl = MainLayoutController.class.getResource("/fxml/landing.fxml");
        if (fxmlUrl == null) throw new IllegalStateException("landing.fxml not found");
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent landingRoot = loader.load();
        stage.setFullScreen(false);
        stage.setScene(new Scene(landingRoot));
        stage.setMaximized(true);
        stage.show();
    }

    private static UserService createUserService() {
        String dbName = System.getenv("MONGODB_DB");
        if (dbName == null || dbName.isBlank()) dbName = "DVCS";
        com.mongodb.client.MongoDatabase database = MongoConnection.getDatabase(dbName);
        return new UserService(new UserRepository(database));
    }
}
