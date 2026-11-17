package com.defragx;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.input.ScrollEvent;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class DefragXApp extends Application {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final NumberFormat INR = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"));

    private final BorderPane root = new BorderPane();
    private final VBox content = new VBox(18);
    private final VBox sidebar = new VBox(7);
    private final ObservableList<BankAccount> accounts = FXCollections.observableArrayList();
    private final ObservableList<TransactionEvent> events = FXCollections.observableArrayList();
    private final ObservableList<SnapshotRow> snapshotRows = FXCollections.observableArrayList();
    private final List<Integer> threadHistory = new ArrayList<>();
    private final RuntimeMonitor runtimeMonitor = new RuntimeMonitor();
    private final IssueDetector issueDetector = new IssueDetector();

    private StorageManager storageManager;
    private SnapshotManager snapshotManager;
    private RollbackManager rollbackManager;
    private Timeline monitorTimer;
    private Timeline snapshotTimer;
    private ScrollPane contentScroll;
    private AnimationTimer scrollAnimator;
    private double targetScrollValue;
    private boolean scrollAnimationActive;
    private Label pageTitle;
    private Label pageSubtitle;
    private Label healthBadge;
    private Label healthDetail;
    private Label sampleTime;
    private Button simulateIssueButton;
    private Label heapValue;
    private Label heapDetail;
    private Label snapshotValue;
    private Label cpuValue;
    private Label cpuDetail;
    private Label threadValue;
    private Canvas threadTrend;
    private Label diagnosticHeapValue;
    private Label diagnosticCpuValue;
    private Label diagnosticThreadsValue;
    private Button diagnosticsIssueButton;
    private ComboBox<BankAccount> fromAccount;
    private ComboBox<BankAccount> toAccount;
    private TextField amountField;
    private TableView<TransactionEvent> eventTable;
    private TableView<SnapshotRow> snapshotTable;
    private TableView<BankAccount> accountTable;
    private String currentPage = "Overview";
    private boolean simulatedIssue;
    private boolean runtimeUnhealthy;
    private boolean lastReportedHealthy = true;
    private int transactionCount;
    private RuntimeMetrics lastMetrics;

    @Override
    public void start(Stage stage) throws Exception {
        storageManager = new StorageManager("SQLITE", resolveDatabasePath());
        events.setAll(storageManager.getEvents());
        snapshotManager = new SnapshotManager(runtimeMonitor, storageManager);
        rollbackManager = new RollbackManager(snapshotManager);
        accounts.addAll(
                new BankAccount("Auraa", "1111", 25000),
                new BankAccount("Supzz", "1811", 12000),
                new BankAccount("GSV", "0701", 18000),
                new BankAccount("GGV", "0510", 15000),
                new BankAccount("SCN", "0515", 15000));

        root.getStyleClass().add("app-root");
        root.setLeft(buildSidebar());
        root.setCenter(buildMainArea());
        Scene scene = new Scene(root, 1440, 940);
        scene.getStylesheets().add(getClass().getResource("/com/defragx/defragx.css").toExternalForm());
        stage.setTitle("DefragX | Banking Resilience Demo");
        stage.setMinWidth(1120);
        stage.setMinHeight(760);
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            if (monitorTimer != null) monitorTimer.stop();
            if (snapshotTimer != null) snapshotTimer.stop();
            if (scrollAnimator != null) scrollAnimator.stop();
            try { if (storageManager != null) storageManager.close(); } catch (Exception ignored) { }
        });
        showOverview();
        stage.show();
        refreshMonitoring();
        monitorTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> refreshMonitoring()));
        monitorTimer.setCycleCount(Timeline.INDEFINITE);
        monitorTimer.play();
        snapshotTimer = new Timeline(new KeyFrame(Duration.minutes(3), e -> createAutomaticSnapshot()));
        snapshotTimer.setCycleCount(Timeline.INDEFINITE);
        snapshotTimer.play();
    }

    private String resolveDatabasePath() throws Exception {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path appData = localAppData == null || localAppData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".defragx")
                : Path.of(localAppData, "DefragX");
        Files.createDirectories(appData);

        Path database = appData.resolve("defragx.db");
        if (Files.notExists(database)) {
            Path existingDatabase = Path.of("defragx.db").toAbsolutePath();
            if (Files.isRegularFile(existingDatabase)) {
                Files.copy(existingDatabase, database);
            }
        }
        return database.toString();
    }

    private Node buildSidebar() {
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(22, 14, 18, 14));
        sidebar.setPrefWidth(218);
        Label brandMark = new Label("DX"); brandMark.getStyleClass().add("brand-mark");
        VBox brandWords = new VBox(2);
        Label brand = new Label("DefragX"); brand.getStyleClass().add("brand-name");
        brandWords.getChildren().add(brand);
        HBox brandRow = new HBox(10, brandMark, brandWords); brandRow.setAlignment(Pos.CENTER_LEFT);

        Label workspace = new Label("CONTROL CENTER"); workspace.getStyleClass().add("section-label");
        sidebar.getChildren().addAll(brandRow, gap(26), workspace,
                navButton("◈", "Overview", this::showOverview),
                navButton("▤", "Accounts", this::showAccounts),
                navButton("⇄", "Transactions", this::showTransactions),
                navButton("◷", "Snapshots", this::showSnapshots),
                navButton("⌁", "Diagnostics", this::showDiagnostics));

        Region spacer = new Region(); VBox.setVgrow(spacer, Priority.ALWAYS);
        VBox note = new VBox(8);
        note.getStyleClass().add("sidebar-note");
        Label demoChip = new Label("SIMULATION MODE"); demoChip.getStyleClass().add("demo-chip");
        note.getChildren().add(demoChip);
        sidebar.getChildren().addAll(spacer, note);
        return sidebar;
    }

    private Node buildMainArea() {
        VBox main = new VBox(); main.getStyleClass().add("main-area");
        HBox topbar = new HBox(14); topbar.getStyleClass().add("topbar");
        topbar.setAlignment(Pos.CENTER_LEFT); topbar.setPadding(new Insets(15, 25, 15, 25));
        VBox titleBox = new VBox(4);
        pageTitle = new Label("Overview"); pageTitle.getStyleClass().add("page-title");
        pageSubtitle = new Label("Runtime health and transaction control"); pageSubtitle.getStyleClass().add("breadcrumb");
        pageSubtitle.setManaged(false); pageSubtitle.setVisible(false);
        titleBox.getChildren().add(pageTitle);
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        VBox healthBlock = new VBox(3); healthBlock.setAlignment(Pos.CENTER_RIGHT);
        healthBadge = new Label("● CHECKING SYSTEM"); healthBadge.getStyleClass().add("health-badge");
        healthDetail = new Label("Collecting live JVM metrics"); healthDetail.getStyleClass().add("health-detail");
        healthBlock.getChildren().addAll(healthBadge, healthDetail);
        sampleTime = new Label("--:--:--"); sampleTime.getStyleClass().add("sample-time");
        simulateIssueButton = new Button("Simulate issue"); simulateIssueButton.getStyleClass().add("warning-button");
        simulateIssueButton.setOnAction(e -> toggleSimulatedIssue());
        topbar.getChildren().addAll(titleBox, spacer, healthBlock, sampleTime, simulateIssueButton);

        contentScroll = new ScrollPane(content); contentScroll.setFitToWidth(true); contentScroll.setPannable(true); contentScroll.getStyleClass().add("content-scroll");
        contentScroll.addEventFilter(ScrollEvent.SCROLL,this::handleSmoothScroll);
        scrollAnimator = new AnimationTimer() {
            @Override public void handle(long now) {
                double current=contentScroll.getVvalue();
                double remaining=targetScrollValue-current;
                if(Math.abs(remaining)<0.001) {
                    contentScroll.setVvalue(targetScrollValue);
                    scrollAnimationActive=false;
                    stop();
                } else {
                    contentScroll.setVvalue(current+remaining*0.22);
                }
            }
        };
        content.setPadding(new Insets(22, 25, 26, 25));
        main.getChildren().addAll(topbar, contentScroll); VBox.setVgrow(contentScroll, Priority.ALWAYS);
        return main;
    }

    private void handleSmoothScroll(ScrollEvent event) {
        if(!"Overview".equals(currentPage)||event.getDeltaY()==0)return;
        double scrollableHeight=content.getBoundsInLocal().getHeight()-contentScroll.getViewportBounds().getHeight();
        if(scrollableHeight<=0)return;
        double currentTarget=scrollAnimationActive?targetScrollValue:contentScroll.getVvalue();
        targetScrollValue=Math.max(0,Math.min(1,currentTarget-event.getDeltaY()/scrollableHeight));
        scrollAnimationActive=true;
        scrollAnimator.start();
        event.consume();
    }

    private void showOverview() {
        setPage("Overview", "Live JVM diagnostics paired with a simulated banking workload");
        content.getChildren().clear();
        HBox intro = new HBox(10); intro.setAlignment(Pos.CENTER_LEFT);
        VBox words = new VBox(4, heading("Banking runtime control"));
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Button snapshot = primaryButton("＋  Create snapshot"); snapshot.setOnAction(e -> createSnapshot());
        Button restore = secondaryButton("↶  Restore Previous Snapshot"); restore.setOnAction(e -> restorePreviousSnapshot());
        intro.getChildren().addAll(words, spacer, restore, snapshot);

        GridPane metrics = buildMetrics();
        HBox mid = new HBox(16); mid.setAlignment(Pos.TOP_LEFT);
        VBox accountPanel = panel(); accountPanel.getChildren().addAll(sectionHeading("DEMO ACCOUNTS", "Balances are simulated in INR"));
        accountTable = buildAccountTable(); accountTable.setPrefHeight(205);
        accountPanel.getChildren().add(accountTable); HBox.setHgrow(accountPanel, Priority.ALWAYS); accountPanel.setPrefWidth(610);
        VBox transferPanel = buildTransferPanel(); transferPanel.setPrefWidth(390); transferPanel.setMinWidth(350);
        mid.getChildren().addAll(accountPanel, transferPanel);

        VBox eventsPanel = panel();
        eventsPanel.getChildren().addAll(sectionHeading("TRANSACTION EVENTS", "Local date and time • newest first"));
        eventTable = buildEventTable(); eventTable.setPrefHeight(230); eventsPanel.getChildren().add(eventTable);

        VBox snapshotsPanel = panel();
        HBox snapshotHeading = new HBox(10); snapshotHeading.setAlignment(Pos.CENTER_LEFT);
        VBox sh = sectionHeading("RUNTIME SNAPSHOTS", "Numbered in creation order and saved to SQLite");
        Region snapshotPush = new Region(); HBox.setHgrow(snapshotPush, Priority.ALWAYS);
        Button viewSnapshots = textButton("Open history  →"); viewSnapshots.setOnAction(e -> showSnapshots());
        snapshotHeading.getChildren().addAll(sh, snapshotPush, viewSnapshots);
        snapshotTable = buildSnapshotTable(); snapshotTable.setPrefHeight(185);
        snapshotsPanel.getChildren().addAll(snapshotHeading, snapshotTable);
        content.getChildren().addAll(intro, metrics, mid, eventsPanel, snapshotsPanel);
        refreshAccounts(); refreshEvents(); refreshSnapshots();
    }

    private GridPane buildMetrics() {
        GridPane grid = new GridPane(); grid.setHgap(12); grid.setVgap(12);
        heapValue = new Label("--"); heapDetail = new Label("Waiting for sample");
        snapshotValue = new Label("0");
        cpuValue = new Label("--"); cpuDetail = new Label("Real process load • normalized 0–1");
        threadValue = new Label("--");
        grid.add(metricCard("JVM HEAP", heapValue, heapDetail, "#35C8F4"), 0, 0);
        grid.add(metricCard("SNAPSHOTS", snapshotValue, new Label("Automatic capture every 3 minutes"), "#8D7BFF"), 1, 0);
        grid.add(metricCard("CPU USAGE", cpuValue, cpuDetail, "#39D6A0"), 2, 0);
        grid.add(threadMetricCard(), 3, 0);
        for (int i=0;i<4;i++) { ColumnConstraints cc=new ColumnConstraints(); cc.setPercentWidth(25); cc.setHgrow(Priority.ALWAYS); grid.getColumnConstraints().add(cc); }
        return grid;
    }

    private VBox buildTransferPanel() {
        VBox box = panel();
        box.getChildren().addAll(sectionHeading("SIMULATE TRANSFER", "Transactions are only a local demo"));
        fromAccount = accountCombo(); toAccount = accountCombo();
        amountField = new TextField(); amountField.setPromptText("Example: 250.00"); amountField.getStyleClass().add("input-field");
        box.getChildren().addAll(fieldBlock("FROM ACCOUNT", fromAccount), fieldBlock("TO ACCOUNT", toAccount), fieldBlock("AMOUNT (INR)", amountField));
        fromAccount.setValue(accounts.get(0)); toAccount.setValue(accounts.get(1));
        Button transfer = primaryButton("Run transaction"); transfer.setMaxWidth(Double.MAX_VALUE); transfer.setOnAction(e -> runTransaction());
        box.getChildren().add(transfer);
        return box;
    }

    private void showAccounts() {
        setPage("Accounts", "Three named demo accounts • all balances stay local to this application");
        content.getChildren().clear();
        VBox box=panel(); box.getChildren().addAll(sectionHeading("REGISTERED DEMO ACCOUNTS", "Account identifiers supplied for the project demo"));
        accountTable=buildAccountTable(); accountTable.setPrefHeight(285); box.getChildren().add(accountTable);
        content.getChildren().addAll(heading("Account directory"), muted("Use the transaction page to move simulated funds between these accounts."), box);
        refreshAccounts();
    }

    private void showTransactions() {
        setPage("Transactions", "Simulated transfers use current local date and time");
        content.getChildren().clear();
        HBox layout=new HBox(16); layout.setAlignment(Pos.TOP_LEFT);
        VBox transfer=buildTransferPanel(); transfer.setPrefWidth(390); transfer.setMinWidth(350);
        VBox eventsPanel=panel(); eventsPanel.getChildren().addAll(sectionHeading("EVENT LOG", "Warnings are reserved for simulated or detected system issues"));
        eventTable=buildEventTable(); eventTable.setPrefHeight(470); eventsPanel.getChildren().add(eventTable); HBox.setHgrow(eventsPanel,Priority.ALWAYS);
        layout.getChildren().addAll(transfer,eventsPanel);
        content.getChildren().addAll(heading("Transaction simulator"), muted("Transfer validation checks amount, available demo balance, account selection, and system health."), layout);
        refreshEvents();
    }

    private void showSnapshots() {
        setPage("Snapshots", "Saved runtime metrics provide named recovery points");
        content.getChildren().clear();
        HBox actionRow=new HBox(10); actionRow.setAlignment(Pos.CENTER_LEFT);
        Button create=primaryButton("＋  Create snapshot"); create.setOnAction(e -> createSnapshot());
        Button rollback=secondaryButton("↶  Restore Previous Snapshot"); rollback.setOnAction(e -> restorePreviousSnapshot());
        actionRow.getChildren().addAll(create,rollback);
        VBox box=panel(); box.getChildren().addAll(sectionHeading("SNAPSHOT HISTORY", "Snapshot 1, Snapshot 2, and so on by creation time"));
        snapshotTable=buildSnapshotTable(); snapshotTable.setPrefHeight(480); box.getChildren().add(snapshotTable);
        content.getChildren().addAll(heading("Recovery points"), muted("Snapshots are captured automatically every 3 minutes. You can also create one manually."), actionRow, box);
        refreshSnapshots();
    }

    private void showDiagnostics() {
        setPage("Diagnostics", "Automatic monitoring samples the live JVM once per second");
        content.getChildren().clear();
        VBox live=panel(); live.getChildren().addAll(sectionHeading("LIVE DIAGNOSTICS", "Heap and thread values come from JVM management beans; CPU comes from the host OS bean"));
        GridPane values=new GridPane(); values.setHgap(14); values.setVgap(14);
        diagnosticHeapValue=new Label("--"); diagnosticCpuValue=new Label("--"); diagnosticThreadsValue=new Label("--"); Label jvm=new Label(runtimeMonitor.getJvmInfo());
        diagnosticHeapValue.getStyleClass().add("diagnostic-value"); diagnosticCpuValue.getStyleClass().add("diagnostic-value"); diagnosticThreadsValue.getStyleClass().add("diagnostic-value"); jvm.getStyleClass().add("diagnostic-value");
        values.add(diagnosticTile("HEAP USED / MAX",diagnosticHeapValue),0,0); values.add(diagnosticTile("CPU LOAD (0–1)",diagnosticCpuValue),1,0);
        values.add(diagnosticTile("ACTIVE THREADS",diagnosticThreadsValue),0,1); values.add(diagnosticTile("JVM",jvm),1,1);
        live.getChildren().add(values);
        diagnosticsIssueButton=primaryButton(simulatedIssue?"✓  Resolve simulated issue":"⚠  Simulate unhealthy state"); diagnosticsIssueButton.setOnAction(e -> toggleSimulatedIssue());
        content.getChildren().addAll(heading("System diagnostics"),muted("No fabricated CPU, heap, thread, or timestamp readings are used."),live,diagnosticsIssueButton);
        refreshDiagnostics();
    }

    private Node diagnosticTile(String title, Label value) {
        VBox tile=new VBox(8); tile.getStyleClass().add("diagnostic-tile"); Label name=new Label(title); name.getStyleClass().add("metric-label"); tile.getChildren().addAll(name,value); return tile;
    }

    private void refreshDiagnostics() {
        if(lastMetrics==null)return;
        if(diagnosticHeapValue!=null)diagnosticHeapValue.setText(formatBytes(lastMetrics.heapUsedBytes())+" / "+formatBytes(lastMetrics.heapMaxBytes()));
        if(diagnosticCpuValue!=null)diagnosticCpuValue.setText(Double.isFinite(lastMetrics.cpuUsage())?String.format(Locale.ROOT,"%.3f",lastMetrics.cpuUsage()):"Unavailable");
        if(diagnosticThreadsValue!=null)diagnosticThreadsValue.setText(Integer.toString(lastMetrics.threadCount()));
    }

    private TableView<BankAccount> buildAccountTable() {
        TableView<BankAccount> table=new TableView<>(accounts); table.getStyleClass().add("dark-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY); table.setPlaceholder(new Label("No accounts configured"));
        TableColumn<BankAccount,String> holder=column("ACCOUNT HOLDER",a->a.holder);
        TableColumn<BankAccount,String> number=column("ACCOUNT NUMBER",a->a.number);
        TableColumn<BankAccount,String> balance=column("DEMO BALANCE",a->INR.format(a.balance));
        table.getColumns().addAll(holder,number,balance); return table;
    }

    private TableView<TransactionEvent> buildEventTable() {
        TableView<TransactionEvent> table=new TableView<>(events); table.getStyleClass().add("dark-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY); table.setPlaceholder(new Label("No events yet. Run a demo transaction to populate this table."));
        TableColumn<TransactionEvent,String> holder=column("ACCOUNT HOLDER",e->e.accountHolder);
        TableColumn<TransactionEvent,String> number=column("ACCOUNT NUMBER",e->e.accountNumber);
        TableColumn<TransactionEvent,String> amount=column("AMOUNT",e->e.amount);
        TableColumn<TransactionEvent,String> date=column("DATE",e->DATE_FORMAT.format(e.timestamp));
        TableColumn<TransactionEvent,String> time=column("TIME",e->TIME_FORMAT.format(e.timestamp));
        TableColumn<TransactionEvent,String> status=column("STATUS",e->e.status);
        status.setCellFactory(c->new TableCell<>() { @Override protected void updateItem(String value,boolean empty){super.updateItem(value,empty); if(empty||value==null){setText(null);getStyleClass().removeAll("status-success","status-warning","status-invalid");} else {setText(value);getStyleClass().removeAll("status-success","status-warning","status-invalid");getStyleClass().add(value.equals("Success")?"status-success":value.equals("Invalid")?"status-invalid":"status-warning");}} });
        table.getColumns().addAll(holder,number,amount,date,time,status,column("DETAILS",e->e.details)); return table;
    }

    private TableView<SnapshotRow> buildSnapshotTable() {
        TableView<SnapshotRow> table=new TableView<>(snapshotRows); table.getStyleClass().add("dark-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY); table.setPlaceholder(new Label("No snapshots yet. Create one to save current JVM metrics."));
        table.getColumns().addAll(column("SNAPSHOT",r->r.name),column("CREATED DATE",r->r.date),column("CREATED TIME",r->r.time),column("STATUS",r->r.status),column("MEMORY / CPU / THREADS",r->r.summary));
        return table;
    }

    private <T> TableColumn<T,String> column(String title, java.util.function.Function<T,String> value) {
        TableColumn<T,String> column=new TableColumn<>(title);
        column.setCellValueFactory(cell->new SimpleStringProperty(value.apply(cell.getValue())));
        return column;
    }

    private void runTransaction() {
        BankAccount source=fromAccount==null?null:fromAccount.getValue();
        BankAccount destination=toAccount==null?null:toAccount.getValue();
        double amount;
        try { amount=Double.parseDouble(amountField.getText().trim()); }
        catch(Exception ex) { recordEvent(source,"—","Invalid","Invalid transaction amount"); return; }
        if(source==null||destination==null){recordEvent(source,INR.format(amount),"Invalid","Select both accounts");return;}
        if(!Double.isFinite(amount)||amount<=0){recordEvent(source,INR.format(amount),"Invalid","Amount must be greater than zero");return;}
        if(source==destination){recordEvent(source,INR.format(amount),"Invalid","Choose two different accounts");return;}
        if(!isSystemHealthy()){recordEvent(source,INR.format(amount),"Warning","System unhealthy: transfer blocked");return;}
        if(source.balance<amount){recordEvent(source,INR.format(amount),"Invalid","Insufficient demo balance");return;}
        transactionCount++;
        if(transactionCount==5){
            try {
                snapshotManager.createRecoveryCheckpoint(encodeBankingState());
                refreshSnapshots();
            } catch(Exception ex) {
                transactionCount--;
                showError("Recovery point could not be saved",ex);
                return;
            }
            BankAccount affected=ThreadLocalRandom.current().nextBoolean()?source:destination;
            double faultBalance=ThreadLocalRandom.current().nextBoolean()?0.0:-1.0;
            affected.balance=faultBalance;
            if(accountTable!=null)accountTable.refresh();
            recordEvent(affected,INR.format(amount),"Warning","Simulated warning on transaction 5: balance changed to "+INR.format(faultBalance)+". Use Restore Previous Snapshot to recover.");
            amountField.clear();
            return;
        }
        source.balance-=amount; destination.balance+=amount;
        recordEvent(source,INR.format(amount),"Success","Transfer to "+destination.holder+" completed");
        if(accountTable!=null)accountTable.refresh();
        amountField.clear();
    }

    private void recordEvent(BankAccount account,String amount,String status,String details) {
        LocalDateTime now=LocalDateTime.now();
        String holder=account==null?"System Monitor":account.holder;
        String number=account==null?"SYSTEM":account.number;
        TransactionEvent event=new TransactionEvent(holder,number,amount,now,status,details);
        try {
            storageManager.saveEvent(event);
        } catch(Exception ex) {
            showError("Event could not be saved",ex);
        }
        events.add(0,event);
        if(events.size()>250)events.remove(events.size()-1);
        if(eventTable!=null)eventTable.refresh();
    }

    private void toggleSimulatedIssue() {
        if(!simulatedIssue) {
            try {
                snapshotManager.createRecoveryCheckpoint(encodeBankingState());
                refreshSnapshots();
            } catch(Exception ex) {
                showError("Recovery point could not be saved",ex);
                return;
            }
            BankAccount sender=fromAccount==null?null:fromAccount.getValue();
            BankAccount receiver=toAccount==null?null:toAccount.getValue();
            BankAccount affected;
            if(sender!=null&&receiver!=null&&sender!=receiver)
                affected=ThreadLocalRandom.current().nextBoolean()?sender:receiver;
            else if(sender!=null)affected=sender;
            else if(receiver!=null)affected=receiver;
            else affected=accounts.get(ThreadLocalRandom.current().nextInt(accounts.size()));
            affected.balance=ThreadLocalRandom.current().nextBoolean()?0.0:-1.0;
            if(accountTable!=null)accountTable.refresh();
            recordEvent(affected,INR.format(affected.balance),"Warning","Simulated unhealthy state: "+affected.holder+" balance set to "+INR.format(affected.balance)+". Use Restore Previous Snapshot to recover.");
        }
        simulatedIssue=!simulatedIssue;
        String label=simulatedIssue?"✓  Resolve simulated issue":"⚠  Simulate unhealthy state";
        if(simulateIssueButton!=null)simulateIssueButton.setText(simulatedIssue?"Resolve issue":"Simulate issue");
        if(diagnosticsIssueButton!=null)diagnosticsIssueButton.setText(label);
        refreshMonitoring();
    }

    private void refreshMonitoring() {
        try {
            lastMetrics=runtimeMonitor.collectMetrics();
            Issue issue=issueDetector.detectIssue(lastMetrics);
            runtimeUnhealthy=issue!=null;
            boolean healthy=isSystemHealthy();
            if(lastReportedHealthy&&!healthy)recordEvent(null,"—","Warning",simulatedIssue?"Simulated runtime issue enabled":"Runtime threshold exceeded: "+issueDetector.getIssueDetails());
            else if(!lastReportedHealthy&&healthy)recordEvent(null,"—","Success","System health restored");
            lastReportedHealthy=healthy;
            if(healthBadge!=null){healthBadge.setText(healthy?"● SYSTEM HEALTHY":"● SYSTEM UNHEALTHY");healthBadge.getStyleClass().removeAll("healthy","unhealthy");healthBadge.getStyleClass().add(healthy?"healthy":"unhealthy");}
            if(healthDetail!=null)healthDetail.setText(simulatedIssue?"Simulated issue active":runtimeUnhealthy?issueDetector.getIssueDetails():"Runtime thresholds normal");
            if(sampleTime!=null)sampleTime.setText(TIME_FORMAT.format(lastMetrics.timestamp()));
            if(heapValue!=null){heapValue.setText(formatBytes(lastMetrics.heapUsedBytes()));heapDetail.setText(formatBytes(lastMetrics.heapMaxBytes())+" maximum  •  "+String.format(Locale.ROOT,"%.1f%% used",lastMetrics.memoryUsage()));}
            if(cpuValue!=null){cpuValue.setText(Double.isFinite(lastMetrics.cpuUsage())?String.format(Locale.ROOT,"%.3f",lastMetrics.cpuUsage()):"Unavailable");cpuDetail.setText(Double.isFinite(lastMetrics.cpuUsage())?"Real process CPU fraction • 0–1":"JVM CPU reading unavailable");}
            if(threadValue!=null)threadValue.setText(Integer.toString(lastMetrics.threadCount()));
            updateThreadTrend(lastMetrics.threadCount());
            refreshDiagnostics();
        } catch(Exception ex) {
            if(healthBadge!=null){healthBadge.setText("● MONITOR ERROR");healthBadge.getStyleClass().removeAll("healthy","unhealthy");healthBadge.getStyleClass().add("unhealthy");}
            if(healthDetail!=null)healthDetail.setText(ex.getClass().getSimpleName()+": "+ex.getMessage());
        }
    }

    private boolean isSystemHealthy(){return !simulatedIssue&&!runtimeUnhealthy;}

    private VBox threadMetricCard() {
        VBox card=new VBox(8); card.getStyleClass().add("metric-card");
        Label name=new Label("LIVE THREADS"); name.getStyleClass().add("metric-label");
        threadValue.getStyleClass().add("metric-value");
        threadTrend=new Canvas(190,24); threadTrend.setMouseTransparent(true);
        Region line=new Region(); line.getStyleClass().add("metric-accent"); line.setStyle("-fx-background-color: #F5B84B;");
        card.getChildren().addAll(name,threadValue,threadTrend,line);
        GridPane.setHgrow(card,Priority.ALWAYS);
        return card;
    }

    private void updateThreadTrend(int count) {
        if(threadHistory.size()==30)threadHistory.remove(0);
        threadHistory.add(count);
        if(threadTrend==null)return;
        GraphicsContext graphics=threadTrend.getGraphicsContext2D();
        double width=threadTrend.getWidth(), height=threadTrend.getHeight();
        graphics.clearRect(0,0,width,height);
        int min=threadHistory.stream().mapToInt(Integer::intValue).min().orElse(count);
        int max=threadHistory.stream().mapToInt(Integer::intValue).max().orElse(count);
        double range=Math.max(1,max-min);
        graphics.setStroke(Color.web("#F5B84B")); graphics.setLineWidth(1.8);
        for(int i=0;i<threadHistory.size();i++) {
            double x=threadHistory.size()==1?width/2.0:width*i/(threadHistory.size()-1.0);
            double y=max==min?height/2.0:height-2-(threadHistory.get(i)-min)*(height-4)/range;
            if(i==0)graphics.beginPath();
            if(i==0)graphics.moveTo(x,y); else graphics.lineTo(x,y);
        }
        if(!threadHistory.isEmpty()) {
            graphics.stroke();
            int latest=threadHistory.get(threadHistory.size()-1);
            double latestY=max==min?height/2.0:height-2-(latest-min)*(height-4)/range;
            graphics.fillOval(width-4,Math.max(0,Math.min(height-4,latestY-2)),4,4);
        }
    }

    private void createSnapshot() {
        try {
            Snapshot snapshot=snapshotManager.createManualSnapshot(encodeBankingState());
            refreshSnapshots();
            recordEvent(null,"—","Success","Manual snapshot saved at "+TIME_FORMAT.format(snapshot.getTimestamp()));
        }
        catch(Exception ex){showError("Snapshot could not be saved",ex);}
    }

    private void createAutomaticSnapshot() {
        try { createCurrentSnapshot(); refreshSnapshots(); }
        catch(Exception ex){System.err.println("Automatic snapshot could not be saved: " + ex.getMessage());}
    }

    private Snapshot createCurrentSnapshot() throws Exception {
        Snapshot snapshot=snapshotManager.createSnapshot(encodeBankingState());
        boolean recoveryPending=snapshotManager.getSnapshots().stream().anyMatch(s->"RECOVERY_CHECKPOINT".equals(s.getStatus()));
        if(recoveryPending)snapshotManager.markIncidentState(snapshot);
        return snapshot;
    }

    private String encodeBankingState() {
        String balances=accounts.stream().map(a->a.number+"="+Double.toString(a.balance)).reduce((a,b)->a+"|"+b).orElse("");
        return Base64.getUrlEncoder().withoutPadding().encodeToString(balances.getBytes(StandardCharsets.UTF_8));
    }

    private Map<String,Double> decodeBankingState(String encoded) {
        if(encoded.isBlank()||encoded.equals("—"))throw new IllegalArgumentException("This older snapshot does not contain account balances. Create a new snapshot before using restore.");
        String balances=new String(Base64.getUrlDecoder().decode(encoded),StandardCharsets.UTF_8);
        Map<String,Double> restored=new HashMap<>();
        for(String entry:balances.split("\\|")) {
            String[] pair=entry.split("=",2);
            if(pair.length!=2)throw new IllegalArgumentException("Snapshot account data is invalid.");
            double balance=Double.parseDouble(pair[1]);
            if(!Double.isFinite(balance)||balance<0)throw new IllegalArgumentException("Snapshot contains an invalid account balance.");
            restored.put(pair[0],balance);
        }
        for(BankAccount account:accounts)if(!restored.containsKey(account.number))throw new IllegalArgumentException("Snapshot is missing account "+account.number+".");
        return restored;
    }

    private void refreshSnapshots() {
        if(snapshotManager==null)return;
        try {
            List<Snapshot> saved=new ArrayList<>(snapshotManager.getSnapshots());
            saved.sort(Comparator.comparing(Snapshot::getTimestamp).thenComparing(Snapshot::getSnapshotId));
            snapshotRows.clear();
            for(int i=saved.size()-1;i>=0;i--){Snapshot s=saved.get(i);int ordinal=i+1;snapshotRows.add(new SnapshotRow("Snapshot "+ordinal,DATE_FORMAT.format(s.getTimestamp()),TIME_FORMAT.format(s.getTimestamp()),snapshotStatusLabel(s.getStatus()),snapshotSummary(s.getState()),s.getSnapshotId()));}
            if(snapshotValue!=null)snapshotValue.setText(Integer.toString(saved.size()));
            if(snapshotTable!=null)snapshotTable.refresh();
        } catch(Exception ex){showError("Snapshot history could not be loaded",ex);}
    }

    private String snapshotSummary(String state) {
        String memory=extract(state,"memoryPercent"); String cpu=extract(state,"cpuFraction");
        String threads=extract(state,"threads");
        String cpuSummary;
        if(!cpu.equals("—"))cpuSummary=cpu.equals("NaN")?"CPU unavailable":formatCpuFraction(cpu);
        else {
            String legacyCpu=extract(state,"cpuPercent");
            cpuSummary=legacyCpu.equals("NaN")||legacyCpu.equals("—")?"CPU unavailable":formatPercent(legacyCpu);
        }
        return formatPercent(memory)+" heap  •  "+cpuSummary+" CPU load  •  "+threads+" threads";
    }
    private String formatPercent(String value){try{return String.format(Locale.ROOT,"%.1f%%",Double.parseDouble(value));}catch(Exception ex){return "—";}}
    private String formatCpuFraction(String value){try{return String.format(Locale.ROOT,"%.3f",Double.parseDouble(value));}catch(Exception ex){return "—";}}
    private String snapshotStatusLabel(String status){return "RECOVERY_CHECKPOINT".equals(status)?"Recovery point":"INCIDENT_STATE".equals(status)?"Fault state":"MANUAL".equals(status)?"Manual":status;}
    private String extract(String state,String key){String prefix=key+"=";for(String item:state.split("; "))if(item.startsWith(prefix))return item.substring(prefix.length());return "—";}

    private void restorePreviousSnapshot() {
        try {
            List<Snapshot> saved=new ArrayList<>(snapshotManager.getSnapshots());
            saved.sort(Comparator.comparing(Snapshot::getTimestamp).thenComparing(Snapshot::getSnapshotId));
            Snapshot snapshot=null;
            Map<String,Double> balances=null;
            int ordinal=0;
            for(int pass=0;pass<2&&snapshot==null;pass++) {
                for(int i=saved.size()-1;i>=0;i--) {
                    Snapshot candidate=saved.get(i);
                    boolean manual="MANUAL".equals(candidate.getStatus());
                    if("INCIDENT_STATE".equals(candidate.getStatus()))continue;
                    if(pass==0&&!manual)continue;
                    if(pass==1&&manual)continue;
                    String candidateState=extract(candidate.getState(),"bankingState");
                    if(candidateState.isBlank()||candidateState.equals("—"))continue;
                    try {
                        balances=decodeBankingState(candidateState);
                        snapshot=candidate;
                        ordinal=i+1;
                        break;
                    } catch(IllegalArgumentException ignored) {
                        // Skip snapshots whose saved account data cannot be restored.
                    }
                }
            }
            if(snapshot==null){showInfo("No restorable snapshot","Create a new snapshot first. Older snapshots do not contain account balances.");return;}
            String snapshotName="Snapshot "+ordinal;
            rollbackManager.rollback(snapshot.getSnapshotId(),"Restore previous banking snapshot");
            snapshotManager.resolveRecoveryCheckpoints();
            for(BankAccount account:accounts)if(balances.containsKey(account.number))account.balance=balances.get(account.number);
            if(simulatedIssue){
                simulatedIssue=false;
                if(simulateIssueButton!=null)simulateIssueButton.setText("Simulate issue");
                if(diagnosticsIssueButton!=null)diagnosticsIssueButton.setText("⚠  Simulate unhealthy state");
            }
            refreshAccounts(); refreshSnapshots();
            recordEvent(null,"—","Success",snapshotName+" restored account balances");
            refreshMonitoring();
            showInfo("Rollback complete",snapshotName+" restored the demo account balances to their saved values. The transaction event history remains available for audit.");
        }
        catch(Exception ex){showError("Recovery failed",ex);}
    }

    private void refreshAccounts(){if(accountTable!=null)accountTable.refresh();}
    private void refreshEvents(){if(eventTable!=null)eventTable.refresh();}
    private void setPage(String title,String subtitle){currentPage=title;pageTitle.setText(title);pageSubtitle.setText(subtitle);if(contentScroll!=null){if(scrollAnimator!=null)scrollAnimator.stop();scrollAnimationActive=false;targetScrollValue=0;contentScroll.setVvalue(0);}}

    private Button navButton(String icon,String name,Runnable action){
        Label glyph=new Label(icon);glyph.getStyleClass().add("nav-icon");Label text=new Label(name);HBox graphic=new HBox(12,glyph,text);graphic.setAlignment(Pos.CENTER_LEFT);
        Button b=new Button(name);b.setGraphic(graphic);b.setText("");b.setMaxWidth(Double.MAX_VALUE);b.setAlignment(Pos.CENTER_LEFT);b.getStyleClass().add("nav-button");
        b.setOnAction(e->{sidebar.lookupAll(".nav-button").forEach(n->n.getStyleClass().remove("active"));b.getStyleClass().add("active");action.run();});return b;
    }

    private ComboBox<BankAccount> accountCombo(){ComboBox<BankAccount> combo=new ComboBox<>(accounts);combo.setMaxWidth(Double.MAX_VALUE);combo.getStyleClass().add("account-combo");combo.setCellFactory(v->accountCell());combo.setButtonCell(accountCell());return combo;}
    private ListCell<BankAccount> accountCell(){return new ListCell<>(){@Override protected void updateItem(BankAccount item,boolean empty){super.updateItem(item,empty);setText(empty||item==null?null:item.holder+"  •  "+item.number);}};}
    private Node fieldBlock(String title,Node field){VBox box=new VBox(6);Label label=new Label(title);label.getStyleClass().add("field-label");box.getChildren().addAll(label,field);return box;}
    private VBox panel(){VBox box=new VBox(14);box.getStyleClass().add("panel");box.setPadding(new Insets(17));return box;}
    private VBox sectionHeading(String title,String subtitle){VBox box=new VBox();Label t=new Label(title);t.getStyleClass().add("panel-title");box.getChildren().add(t);return box;}
    private Label heading(String text){Label l=new Label(text);l.getStyleClass().add("welcome-title");return l;}
    private Label muted(String text){Label l=new Label(text);l.getStyleClass().add("muted");l.setVisible(false);l.setManaged(false);return l;}
    private Node metricCard(String title,Label value,Label detail,String accent){VBox card=new VBox(8);card.getStyleClass().add("metric-card");Label name=new Label(title);name.getStyleClass().add("metric-label");value.getStyleClass().add("metric-value");Region line=new Region();line.getStyleClass().add("metric-accent");line.setStyle("-fx-background-color: "+accent+";");card.getChildren().addAll(name,value,line);GridPane.setHgrow(card,Priority.ALWAYS);return card;}
    private Button primaryButton(String text){Button b=new Button(text);b.getStyleClass().add("primary-button");return b;}
    private Button secondaryButton(String text){Button b=new Button(text);b.getStyleClass().add("secondary-button");return b;}
    private Button textButton(String text){Button b=new Button(text);b.getStyleClass().add("text-button");return b;}
    private Region gap(double h){Region r=new Region();r.setMinHeight(h);r.setPrefHeight(h);return r;}
    private void showInfo(String title,String message){Alert a=new Alert(Alert.AlertType.INFORMATION);a.setTitle(title);a.setHeaderText(title);a.setContentText(message);a.showAndWait();}
    private void showError(String title,Exception ex){Alert a=new Alert(Alert.AlertType.ERROR);a.setTitle(title);a.setHeaderText(title);a.setContentText(ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage());a.showAndWait();}
    private String formatBytes(long bytes){if(bytes<0)return"Unavailable";if(bytes<1024*1024)return String.format(Locale.ROOT,"%.0f KB",bytes/1024.0);return String.format(Locale.ROOT,"%.1f MB",bytes/(1024.0*1024.0));}

    private static final class BankAccount {
        final String holder,number; double balance;
        BankAccount(String holder,String number,double balance){this.holder=holder;this.number=number;this.balance=balance;}
        @Override public String toString(){return holder+" - "+number;}
    }
    private static final class SnapshotRow {
        final String name,date,time,status,summary,id;
        SnapshotRow(String name,String date,String time,String status,String summary,String id){this.name=name;this.date=date;this.time=time;this.status=status;this.summary=summary;this.id=id;}
    }

    public static void main(String[] args){launch(args);}
}
