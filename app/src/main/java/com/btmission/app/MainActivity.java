package com.btmission.app;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.animation.LinearInterpolator;
import android.view.HapticFeedbackConstants;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;
import java.util.List;

public class MainActivity extends Activity {
    private static final UUID SERVICE_UUID = UUID.fromString("aee04821-1973-4e1f-a590-e84b10d580e7");
    private static final UUID CHAR_UUID = UUID.fromString("cde07b1a-889b-44b7-a99f-c888dddac729");
    private static final int REQUEST_PERMISSIONS = 10;
    private static final int REQUEST_BLUETOOTH = 11;
    private static final int BG = Color.rgb(255, 248, 239);
    private static final int CARD = Color.WHITE;
    private static final int INK = Color.rgb(20, 37, 59);
    private static final int MUTED = Color.rgb(105, 120, 138);
    private static final int ORANGE = Color.rgb(255, 104, 45);
    private static final int GREEN = Color.rgb(35, 160, 112);
    private static final int BLUE = Color.rgb(31, 111, 190);
    private static final int BORDER = Color.rgb(239, 225, 211);
    private static final String BINARY_VALUE = "[binary]";
    private static final String EMPTY_VALUE = "[empty]";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic characteristic;
    private ScanCallback scanCallback;
    private AlertDialog scanDialog;
    private ArrayAdapter<String> deviceListAdapter;
    private TextView scanEmptyLabel;
    private final ArrayList<BluetoothDevice> foundDevices = new ArrayList<>();
    private final ArrayList<String> foundNames = new ArrayList<>();
    private final ArrayList<Boolean> foundMatches = new ArrayList<>();
    private final HashSet<String> foundAddresses = new HashSet<>();
    private Runnable scanTimeout;
    private Runnable operationTimeout;
    private boolean scanning = false;
    private boolean connected = false;
    private boolean busy = false;
    private boolean payloadEdited = false;
    private boolean updatingPayload = false;
    private boolean payloadExpanded = false;
    private boolean english = false;
    private boolean motionEnabled = true;
    private boolean logExpanded = false;
    private int phase = 0; // 0 first read, 1 write, 2 second read, 3 complete
    private int pendingRead = 0;
    private String lastPayload = "";
    private String deviceName = "";
    private String firstText = "";
    private String finalText = "";
    private String firstHexText = "";
    private String finalHexText = "";
    private String messageTh = "แตะค้นหาเพื่อเริ่มภารกิจ";
    private String messageEn = "Tap scan to start your mission";
    private boolean messageError = false;
    private final ArrayList<String[]> logEntries = new ArrayList<>();

    private TextView connectionLabel;
    private TextView messageLabel;
    private final TextView[] progressDots = new TextView[4];
    private final TextView[] progressLabels = new TextView[4];
    private TextView heroSubtitle;
    private TextView heroHint;
    private TextView deviceHeading;
    private TextView firstHeading;
    private TextView firstCaption;
    private TextView secondHeading;
    private TextView secondCaption;
    private TextView thirdHeading;
    private TextView thirdCaption;
    private TextView yourNameLabel;
    private TextView buddyNameLabel;
    private TextView payloadLabel;
    private TextView payloadSummary;
    private LinearLayout payloadDetails;
    private Button payloadToggleButton;
    private TextView resultLabel;
    private TextView logText;
    private Button languageButton;
    private Button motionButton;
    private HeroArtView heroArt;
    private Button logButton;
    private Button copyButton;
    private Button shareButton;
    private Button resetButton;
    private TextView firstValue;
    private TextView firstHex;
    private TextView writeValue;
    private TextView finalValue;
    private TextView finalHex;
    private TextView byteCount;
    private Button connectButton;
    private Button disconnectButton;
    private Button firstReadButton;
    private Button writeButton;
    private Button secondReadButton;
    private EditText yourName;
    private EditText buddyName;
    private EditText payload;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        english = getPreferences(MODE_PRIVATE).getBoolean("english", false);
        motionEnabled = getPreferences(MODE_PRIVATE).getBoolean("motion_enabled", true);
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
        buildUi();
        refreshUi();
        if (adapter == null) showMessage("โทรศัพท์นี้ไม่รองรับ Bluetooth", "This phone does not support Bluetooth", true);
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private GradientDrawable shape(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }
    private GradientDrawable outlined(int color, int stroke, float radius) {
        GradientDrawable drawable = shape(color, radius);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }
    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }
    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }
    private LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width < 0 ? width : dp(width), height < 0 ? height : dp(height));
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }
    private void gap(LinearLayout parent, int height) {
        View spacer = new View(this);
        parent.addView(spacer, lp(1, height));
    }
        private LinearLayout card() {
        LinearLayout view = column();
        view.setPadding(dp(15), dp(14), dp(15), dp(14));
        view.setBackground(outlined(CARD, BORDER, 19));
        view.setElevation(dp(3));
        return view;
    }
    private Button button(String label, boolean primary) {
        Button view = new Button(this);
        view.setText(label);
        view.setTextSize(14);
        view.setAllCaps(false);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(primary ? Color.WHITE : INK);
        view.setBackground(shape(primary ? ORANGE : Color.rgb(246, 240, 231), 12));
        view.setMinHeight(dp(43));
        view.setPadding(dp(12), 0, dp(12), 0);
        return view;
    }
    private EditText input(String hint) {
        EditText view = new EditText(this);
        view.setSingleLine(true);
        view.setTextSize(14);
        view.setTextColor(INK);
        view.setHintTextColor(Color.rgb(151, 159, 169));
        view.setHint(hint);
        view.setPadding(dp(11), dp(7), dp(11), dp(7));
        view.setBackground(outlined(Color.WHITE, Color.rgb(214, 221, 228), 10));
        return view;
    }
    private TextView valueBox(String value, boolean finalResult) {
        TextView view = text(value, finalResult ? 23 : 20, finalResult ? GREEN : INK, true);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(13), dp(7), dp(13), dp(7));
        view.setBackground(shape(finalResult ? Color.rgb(231, 250, 236) : Color.rgb(245, 248, 251), 11));
        view.setMinHeight(dp(48));
        return view;
    }
    private void section(LinearLayout parent, View view) {
        parent.addView(view, lp(-1, -2));
        gap(parent, 11);
    }
    private TextView stageBadge(String number, int color) {
        TextView badge = text(number, 16, Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(shape(color, 99));
        return badge;
    }
    private LinearLayout stageHeader(String number, TextView title, int color) {
        LinearLayout row = row();
        row.addView(stageBadge(number, color), lp(30, 30));
        LinearLayout.LayoutParams titleLp = lp(-1, -2);
        titleLp.leftMargin = dp(9);
        row.addView(title, titleLp);
        return row;
    }
    private LinearLayout nameField(TextView label, EditText edit) {
        LinearLayout field = column();
        field.addView(label);
        gap(field, 5);
        field.addView(edit, lp(-1, 42));
        return field;
    }
    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        if (Build.VERSION.SDK_INT >= 35) {
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                int top = insets.getInsets(WindowInsets.Type.statusBars()).top;
                int bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                scroll.setPadding(0, top, 0, bottom);
                return insets;
            });
        }
        LinearLayout content = column();
        content.setPadding(dp(14), dp(7), dp(14), dp(20));
        scroll.addView(content);
        setContentView(scroll);

        FrameLayout hero = new FrameLayout(this);
        hero.setBackground(shape(Color.rgb(255, 161, 72), 22));
        hero.setClipToOutline(true);
        heroArt = new HeroArtView();
        hero.addView(heroArt, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout heroContent = column();
        heroContent.setPadding(dp(18), dp(9), dp(18), 0);
        FrameLayout.LayoutParams heroContentParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        hero.addView(heroContent, heroContentParams);
        LinearLayout heroTop = row();
        TextView heroKicker = text("✦  CLASS BLUETOOTH PROJECT", 10, INK, true);
        heroTop.addView(heroKicker, new LinearLayout.LayoutParams(0, -2, 1));
        motionButton = button("II", false);
        motionButton.setTextSize(12);
        motionButton.setMinHeight(dp(30));
        motionButton.setBackground(outlined(Color.rgb(255, 248, 226), Color.rgb(230, 123, 58), 99));
        LinearLayout.LayoutParams motionParams = lp(36, 31);
        motionParams.rightMargin = dp(5);
        heroTop.addView(motionButton, motionParams);
        languageButton = button("EN", false);
        languageButton.setTextSize(12);
        languageButton.setMinHeight(dp(30));
        languageButton.setBackground(outlined(Color.rgb(255, 248, 226), Color.rgb(230, 123, 58), 99));
        heroTop.addView(languageButton, lp(58, 31));
        heroContent.addView(heroTop);
        gap(heroContent, 8);
        heroContent.addView(text("BLE Mission", 26, INK, true));
        heroSubtitle = text("อ่านค่า • ส่งชื่อ • รับผลเกรด", 13, INK, true);
        heroContent.addView(heroSubtitle);
        heroHint = text("พิชิตภารกิจ แล้วรับผลจากอุปกรณ์จริง", 11, INK, false);
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        hintParams.setMargins(dp(18), 0, dp(18), dp(7));
        heroHint.setPadding(dp(9), dp(5), dp(9), dp(5));
        heroHint.setBackground(shape(Color.argb(175, 255, 248, 226), 8));
        hero.addView(heroHint, hintParams);
        section(content, hero);
        hero.getLayoutParams().height = dp(150);

        LinearLayout progress = card();
        progress.setPadding(dp(9), dp(9), dp(9), dp(8));
        LinearLayout progressRow = row();
        String[] numbers = {"1", "2", "3", "4"};
        for (int i = 0; i < 4; i++) {
            LinearLayout part = column();
            part.setGravity(Gravity.CENTER);
            progressDots[i] = stageBadge(numbers[i], i == 0 ? ORANGE : Color.rgb(206, 216, 222));
            part.addView(progressDots[i], lp(29, 29));
            gap(part, 3);
            progressLabels[i] = text("", 10, MUTED, true);
            progressLabels[i].setGravity(Gravity.CENTER);
            part.addView(progressLabels[i]);
            progressRow.addView(part, new LinearLayout.LayoutParams(0, -2, 1));
        }
        progress.addView(progressRow);
        section(content, progress);

        LinearLayout deviceCard = card();
        LinearLayout deviceRow = row();
        deviceHeading = text("อุปกรณ์ของอาจารย์", 16, INK, true);
        deviceRow.addView(deviceHeading, new LinearLayout.LayoutParams(0, -2, 1));
        connectionLabel = text("", 11, MUTED, true);
        connectionLabel.setPadding(dp(9), dp(5), dp(9), dp(5));
        deviceRow.addView(connectionLabel);
        deviceCard.addView(deviceRow);
        gap(deviceCard, 6);
        LinearLayout deviceButtons = row();
        connectButton = button("", true);
        deviceButtons.addView(connectButton, new LinearLayout.LayoutParams(0, dp(43), 1));
        disconnectButton = button("", false);
        LinearLayout.LayoutParams disconnectLp = lp(-2, 43);
        disconnectLp.leftMargin = dp(7);
        deviceButtons.addView(disconnectButton, disconnectLp);
        deviceCard.addView(deviceButtons);
        gap(deviceCard, 3);
        messageLabel = text("", 11, MUTED, false);
        deviceCard.addView(messageLabel);
        section(content, deviceCard);

        LinearLayout first = card();
        firstHeading = text("", 17, INK, true);
        first.addView(stageHeader("1", firstHeading, BLUE));
        gap(first, 4);
        firstCaption = text("", 11, MUTED, false);
        first.addView(firstCaption);
        gap(first, 9);
        LinearLayout firstRow = row();
        firstValue = valueBox("", false);
        firstRow.addView(firstValue, new LinearLayout.LayoutParams(0, dp(47), 1));
        firstReadButton = button("", false);
        firstReadButton.setTextColor(ORANGE);
        firstReadButton.setBackground(shape(Color.rgb(255, 236, 222), 11));
        LinearLayout.LayoutParams readLp = lp(104, 47);
        readLp.leftMargin = dp(7);
        firstRow.addView(firstReadButton, readLp);
        first.addView(firstRow);
        firstHex = text("", 10, MUTED, false);
        first.addView(firstHex);
        section(content, first);

        LinearLayout second = card();
        secondHeading = text("", 17, INK, true);
        second.addView(stageHeader("2", secondHeading, ORANGE));
        gap(second, 4);
        secondCaption = text("", 11, MUTED, false);
        second.addView(secondCaption);
        gap(second, 10);
        LinearLayout names = row();
        yourNameLabel = text("", 11, MUTED, true);
        yourName = input("");
        buddyNameLabel = text("", 11, MUTED, true);
        buddyName = input("");
        names.addView(nameField(yourNameLabel, yourName), new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams buddyLp = new LinearLayout.LayoutParams(0, -2, 1);
        buddyLp.leftMargin = dp(9);
        names.addView(nameField(buddyNameLabel, buddyName), buddyLp);
        second.addView(names);
        gap(second, 7);
        LinearLayout payloadHeader = row();
        payloadSummary = text("", 11, MUTED, false);
        payloadSummary.setMaxLines(1);
        payloadSummary.setEllipsize(android.text.TextUtils.TruncateAt.END);
        payloadHeader.addView(payloadSummary, new LinearLayout.LayoutParams(0, -2, 1));
        payloadToggleButton = button("", false);
        payloadToggleButton.setTextSize(11);
        payloadHeader.addView(payloadToggleButton, lp(95, 30));
        second.addView(payloadHeader);
        payloadDetails = column();
        gap(payloadDetails, 6);
        payloadLabel = text("", 11, MUTED, true);
        payloadDetails.addView(payloadLabel);
        gap(payloadDetails, 4);
        payload = input("");
        payloadDetails.addView(payload, lp(-1, 42));
        gap(payloadDetails, 4);
        byteCount = text("0 bytes UTF-8", 10, MUTED, false);
        payloadDetails.addView(byteCount);
        second.addView(payloadDetails);
        gap(second, 7);
        writeButton = button("", true);
        second.addView(writeButton, lp(-1, 43));
        writeValue = text("", 11, GREEN, true);
        second.addView(writeValue);
        section(content, second);

        LinearLayout third = card();
        thirdHeading = text("", 17, INK, true);
        third.addView(stageHeader("3", thirdHeading, Color.rgb(228, 164, 49)));
        gap(third, 4);
        thirdCaption = text("", 11, MUTED, false);
        third.addView(thirdCaption);
        gap(third, 9);
        secondReadButton = button("", true);
        third.addView(secondReadButton, lp(-1, 43));
        gap(third, 9);
        resultLabel = text("", 10, GREEN, true);
        third.addView(resultLabel);
        gap(third, 5);
        finalValue = valueBox("", true);
        third.addView(finalValue, lp(-1, 54));
        finalHex = text("", 10, MUTED, false);
        third.addView(finalHex);
        gap(third, 8);
        LinearLayout resultActions = row();
        copyButton = button("", false);
        shareButton = button("", false);
        resetButton = button("", false);
        resultActions.addView(copyButton, new LinearLayout.LayoutParams(0, dp(40), 1));
        LinearLayout.LayoutParams shareLp = new LinearLayout.LayoutParams(0, dp(40), 1);
        shareLp.leftMargin = dp(6);
        resultActions.addView(shareButton, shareLp);
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(0, dp(40), 1);
        resetLp.leftMargin = dp(6);
        resultActions.addView(resetButton, resetLp);
        third.addView(resultActions);
        section(content, third);

        LinearLayout logCard = card();
        logButton = button("", false);
        logCard.addView(logButton, lp(-1, 38));
        logText = text("", 11, MUTED, false);
        logText.setPadding(dp(4), dp(8), dp(4), dp(2));
        logCard.addView(logText);
        section(content, logCard);
        TextView footer = text("SERVICE  aee04821...  •  CHARACTERISTIC  cde07b1a...", 10, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        content.addView(footer);

        languageButton.setOnClickListener(v -> {
            english = !english;
            getPreferences(MODE_PRIVATE).edit().putBoolean("english", english).apply();
            refreshUi();
        });
        motionButton.setOnClickListener(v -> {
            motionEnabled = !motionEnabled;
            getPreferences(MODE_PRIVATE).edit().putBoolean("motion_enabled", motionEnabled).apply();
            heroArt.setAnimationEnabled(motionEnabled);
            refreshUi();
        });
        connectButton.setOnClickListener(v -> beginScan());
        disconnectButton.setOnClickListener(v -> { if (gatt != null) gatt.disconnect(); });
        firstReadButton.setOnClickListener(v -> read(1));
        writeButton.setOnClickListener(v -> writeNames());
        payloadToggleButton.setOnClickListener(v -> { payloadExpanded = !payloadExpanded; refreshUi(); });
        secondReadButton.setOnClickListener(v -> read(2));
        copyButton.setOnClickListener(v -> copyReport());
        shareButton.setOnClickListener(v -> shareReport());
        resetButton.setOnClickListener(v -> {
            resetMission();
            addLog("เริ่มภารกิจใหม่", "New mission started");
            showMessage("เริ่มใหม่ได้ กดอ่านค่าครั้งแรก", "Ready to restart. Read the initial value", false);
            refreshUi();
        });
        logButton.setOnClickListener(v -> { logExpanded = !logExpanded; refreshUi(); });
        TextWatcher namesWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updatePayload(); }
            @Override public void afterTextChanged(Editable s) { }
        };
        yourName.addTextChangedListener(namesWatcher);
        buddyName.addTextChangedListener(namesWatcher);
        payload.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!updatingPayload) payloadEdited = true;
                byteCount.setText(payload.getText().toString().getBytes(StandardCharsets.UTF_8).length + " bytes UTF-8");
                refreshUi();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }
    private String tr(String thai, String englishText) { return english ? englishText : thai; }
    private String displayValue(String value, boolean inEnglish) {
        if (BINARY_VALUE.equals(value)) return inEnglish ? "Binary data (see HEX)" : "ข้อมูลไบนารี (ดู HEX)";
        if (EMPTY_VALUE.equals(value)) return inEnglish ? "(empty)" : "(ค่าว่าง)";
        return value;
    }
    private String displayValue(String value) { return displayValue(value, english); }
    private void updatePayload() {
        if (!payloadEdited) {
            updatingPayload = true;
            payload.setText(yourName.getText().toString().trim() + "," + buddyName.getText().toString().trim());
            updatingPayload = false;
        }
        refreshUi();
    }
    private void showMessage(String thai, String englishText, boolean error) {
        messageTh = thai;
        messageEn = englishText;
        messageError = error;
        if (messageLabel != null) {
            messageLabel.setText(tr(thai, englishText));
            messageLabel.setTextColor(error ? Color.rgb(190, 70, 63) : MUTED);
        }
    }
    private void addLog(String thai, String englishText) {
        String time = new java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(new java.util.Date());
        logEntries.add(new String[]{time, thai, englishText});
        if (logEntries.size() > 20) logEntries.remove(0);
        renderLog();
    }
    private void renderLog() {
        if (logText == null) return;
        logButton.setText(tr(logExpanded ? "▲ ซ่อนบันทึกภารกิจ" : "▼ ดูบันทึกภารกิจ",
                logExpanded ? "▲ Hide mission log" : "▼ View mission log"));
        logText.setVisibility(logExpanded ? View.VISIBLE : View.GONE);
        if (!logExpanded) return;
        if (logEntries.isEmpty()) { logText.setText(tr("ยังไม่มีรายการ", "No activity yet")); return; }
        StringBuilder value = new StringBuilder();
        for (String[] line : logEntries) {
            if (value.length() > 0) value.append('\n');
            value.append(line[0]).append("  ").append(tr(line[1], line[2]));
        }
        logText.setText(value.toString());
    }
    private String report() {
        return "BLE Mission\n" + tr("ชื่อ", "Name") + ": " + yourName.getText().toString().trim() +
                "\n" + tr("เพื่อน", "Buddy") + ": " + buddyName.getText().toString().trim() +
                "\n" + tr("อ่านครั้งแรก", "Initial read") + ": " + displayValue(firstText) +
                "\n" + tr("ส่ง", "Sent") + ": " + lastPayload +
                "\n" + tr("ผลจากอุปกรณ์", "Device result") + ": " + displayValue(finalText);
    }
    private void copyReport() {
        if (phase != 3) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("BLE Mission", report()));
        showMessage("คัดลอกผลแล้ว", "Result copied", false);
    }
    private void shareReport() {
        if (phase != 3) return;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, report());
        startActivity(Intent.createChooser(intent, tr("แชร์ผลภารกิจ", "Share mission result")));
    }
    private void alpha(Button button) { button.setAlpha(button.isEnabled() ? 1f : .43f); }
    private void renderTexts() {
        languageButton.setText(english ? "ไทย" : "EN");
        heroSubtitle.setText(tr("เชื่อมต่อ • อ่าน • ส่ง • รับผล", "Connect • Read • Send • Result"));
        heroHint.setText(tr("พิชิตภารกิจ แล้วรับผลจากอุปกรณ์จริง", "Complete the mission with real device data"));
        motionButton.setText(motionEnabled ? "II" : "▶");
        motionButton.setContentDescription(tr(motionEnabled ? "หยุดภาพเคลื่อนไหว" : "เล่นภาพเคลื่อนไหว",
                motionEnabled ? "Pause animation" : "Play animation"));
        String[] th = {"เชื่อมต่อ", "อ่าน", "ส่ง", "ผล"};
        String[] en = {"CONNECT", "READ", "SEND", "RESULT"};
        for (int i = 0; i < 4; i++) progressLabels[i].setText(english ? en[i] : th[i]);
        deviceHeading.setText(tr("อุปกรณ์ของอาจารย์", "CLASS DEVICE"));
        connectionLabel.setText(connected ? "●  " + tr("เชื่อมต่อ", "Connected") + ": " + deviceName :
                "●  " + tr("ยังไม่เชื่อมต่อ", "Disconnected"));
        connectionLabel.setTextColor(connected ? GREEN : MUTED);
        connectionLabel.setBackground(shape(connected ? Color.rgb(225, 247, 233) : Color.rgb(242, 244, 246), 99));
        connectButton.setText(tr("⌁  ค้นหา BLE", "⌁  Scan BLE"));
        disconnectButton.setText(tr("ตัดการเชื่อมต่อ", "Disconnect"));
        messageLabel.setText(tr(messageTh, messageEn));
        messageLabel.setTextColor(messageError ? Color.rgb(190, 70, 63) : MUTED);
        firstHeading.setText(tr("อ่านค่าครั้งแรก", "Read current value"));
        firstCaption.setText(tr("อ่าน Characteristic ก่อนส่งชื่อ", "Read the characteristic before sending names"));
        firstReadButton.setText(tr("อ่านค่า", "Read"));
        firstValue.setText(phase > 0 ? displayValue(firstText) : tr("ยังไม่ได้อ่าน", "Not read yet"));
        firstHex.setText(phase > 0 ? "HEX  " + firstHexText : "");
        secondHeading.setText(tr("ส่งชื่อคู่ทำภารกิจ", "Send your team names"));
        secondCaption.setText(tr("ส่งชื่อคุณและเพื่อนไปยังอุปกรณ์", "Write your name and your buddy's name"));
        yourNameLabel.setText(tr("ชื่อของคุณ", "YOUR NAME"));
        buddyNameLabel.setText(tr("ชื่อเพื่อน", "BUDDY NAME"));
        payloadLabel.setText(tr("ข้อความที่จะส่ง • แก้รูปแบบได้", "MESSAGE TO SEND • EDITABLE"));
        yourName.setHint(tr("ชื่อของคุณ", "Your name"));
        buddyName.setHint(tr("ชื่อเพื่อน", "Buddy name"));
        payload.setHint(tr("ชื่อคุณ,ชื่อเพื่อน", "Your name,Buddy name"));
        String currentPayload = payload.getText().toString().trim();
        payloadSummary.setText(tr("ส่ง: ", "Payload: ") + (currentPayload.isEmpty() ?
                tr("ชื่อคุณ,ชื่อเพื่อน", "Your name,Buddy name") : currentPayload));
        payloadToggleButton.setText(tr(payloadExpanded ? "ซ่อนรูปแบบ" : "แก้รูปแบบ", payloadExpanded ? "Hide format" : "Edit format"));
        payloadDetails.setVisibility(payloadExpanded ? View.VISIBLE : View.GONE);
        writeButton.setText(tr("➤  ส่งชื่อให้อุปกรณ์", "➤  Send to device"));
        writeValue.setText(phase >= 2 ? tr("✓ ส่งสำเร็จ: ", "✓ Sent: ") + lastPayload : "");
        thirdHeading.setText(tr("เปิดผลทำนายเกรด", "Reveal your grade"));
        thirdCaption.setText(tr("อ่านค่าจาก Characteristic อีกครั้ง", "Read the characteristic again"));
        secondReadButton.setText(tr("★  อ่านผลจากอุปกรณ์", "★  Read device result"));
        resultLabel.setText(phase == 3 ? tr("🏆 ภารกิจสำเร็จ • ผลจากอุปกรณ์", "🏆 MISSION COMPLETE • DEVICE RESULT") :
                tr("ผลจากอุปกรณ์", "RESULT FROM DEVICE"));
        finalValue.setText(phase == 3 ? displayValue(finalText) : tr("รอผลจากอุปกรณ์", "Waiting for device"));
        finalHex.setText(phase == 3 ? "HEX  " + finalHexText : "");
        copyButton.setText(tr("คัดลอก", "Copy"));
        shareButton.setText(tr("แชร์", "Share"));
        resetButton.setText(tr("เริ่มใหม่", "Restart"));
        renderLog();
    }
    private void refreshUi() {
        if (connectButton == null) return;
        heroArt.setMissionComplete(phase == 3);
        heroArt.setAnimationEnabled(motionEnabled);
        connectButton.setEnabled(adapter != null && !busy && !connected);
        disconnectButton.setEnabled(connected && !busy);
        firstReadButton.setEnabled(connected && !busy && phase == 0);
        writeButton.setEnabled(connected && !busy && phase == 1 &&
                !yourName.getText().toString().trim().isEmpty() &&
                !buddyName.getText().toString().trim().isEmpty() &&
                !payload.getText().toString().trim().isEmpty());
        secondReadButton.setEnabled(connected && !busy && phase == 2);
        copyButton.setEnabled(phase == 3);
        shareButton.setEnabled(phase == 3);
        resetButton.setEnabled(connected && !busy && phase > 0);
        for (Button button : new Button[]{connectButton, disconnectButton, firstReadButton, writeButton,
                secondReadButton, copyButton, shareButton, resetButton}) alpha(button);
        for (int i = 0; i < 4; i++) {
            boolean completed = (i == 0 && connected) || (i > 0 && phase >= i);
            boolean active = (i == 0 && !connected) || (connected && phase == i - 1);
            progressDots[i].setText(completed ? "✓" : String.valueOf(i + 1));
            progressDots[i].setTextColor(Color.WHITE);
            progressDots[i].setBackground(shape(completed ? GREEN : active ? ORANGE : Color.rgb(202, 210, 218), 99));
        }
        renderTexts();
    }

    private class HeroArtView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        private float frame = 0f;
        private boolean animationEnabled = true;
        private boolean missionComplete = false;
        HeroArtView() {
            super(MainActivity.this);
            animator.setDuration(4200);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(value -> {
                frame = (float) value.getAnimatedValue();
                invalidate();
            });
        }
        void setAnimationEnabled(boolean enabled) {
            if (animationEnabled == enabled) return;
            animationEnabled = enabled;
            updateAnimation();
        }
        void setMissionComplete(boolean complete) {
            if (missionComplete == complete) return;
            missionComplete = complete;
            invalidate();
        }
        private void updateAnimation() {
            if (animationEnabled && isAttachedToWindow() && getWindowVisibility() == View.VISIBLE) {
                if (!animator.isStarted()) animator.start();
            } else {
                animator.cancel();
                frame = 0f;
                invalidate();
            }
        }
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); updateAnimation(); }
        @Override protected void onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow(); }
        @Override protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            updateAnimation();
        }
        private void fill(Canvas c, int color, Path path) { paint.setShader(null); paint.setColor(color); c.drawPath(path, paint); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save();
            canvas.scale(getWidth() / 360f, getHeight() / 175f);
            float wave = (float) Math.sin(frame * Math.PI * 2);
            float shimmer = (float) Math.sin(frame * Math.PI * 4);
            paint.setShader(new LinearGradient(0, 0, 0, 175, 0xFFFFB052, 0xFFFF7443, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, 360, 175, paint);
            paint.setShader(null);
            paint.setColor(0x66FFE9A2);
            canvas.drawCircle(283, 56, 33 + 2 * shimmer, paint);
            paint.setColor(0x88FFFFFF);
            canvas.drawOval(225 + 12 * frame, 23, 265 + 12 * frame, 32, paint);
            canvas.drawOval(278 - 15 * frame, 17, 326 - 15 * frame, 27, paint);
            Path far = new Path();
            far.moveTo(0, 150); far.cubicTo(60, 110, 120, 154, 174, 122);
            far.cubicTo(235, 90, 295, 124, 360, 100); far.lineTo(360, 175); far.lineTo(0, 175); far.close();
            fill(canvas, 0xFFFFBD75, far);
            Path near = new Path();
            near.moveTo(0, 166); near.cubicTo(77, 131, 129, 169, 207, 141);
            near.cubicTo(278, 116, 309, 158, 360, 132); near.lineTo(360, 175); near.lineTo(0, 175); near.close();
            fill(canvas, 0xFFEF8756, near);
            Path hill = new Path();
            hill.moveTo(0, 175); hill.cubicTo(90, 153, 167, 182, 230, 153);
            hill.cubicTo(296, 132, 330, 169, 360, 156); hill.lineTo(360, 175); hill.close();
            fill(canvas, 0xFFD96D4B, hill);
            paint.setColor(0xFF42245D); paint.setStrokeWidth(3);
            canvas.drawLine(318, 102, 318, 140, paint);
            Path flag = new Path(); flag.moveTo(319, 103); flag.lineTo(345, 110); flag.lineTo(319, 117); flag.close();
            fill(canvas, 0xFFFFE39F, flag);
            float bob = animationEnabled ? 4 * wave : 0;
            float launch = missionComplete ? 18 : 0;
            canvas.save(); canvas.translate(185, -bob - launch); canvas.rotate(-25 + 2 * wave, 65, 126);
            Path rocket = new Path();
            rocket.moveTo(65, 99); rocket.cubicTo(80, 108, 80, 131, 65, 143);
            rocket.cubicTo(50, 130, 50, 109, 65, 99); rocket.close();
            fill(canvas, 0xFF2F2D70, rocket);
            Path finL = new Path(); finL.moveTo(55, 126); finL.lineTo(45, 137); finL.lineTo(57, 135); finL.close(); fill(canvas, 0xFF302A70, finL);
            Path finR = new Path(); finR.moveTo(75, 126); finR.lineTo(86, 137); finR.lineTo(73, 135); finR.close(); fill(canvas, 0xFF302A70, finR);
            paint.setColor(Color.WHITE); canvas.drawCircle(65, 119, 5, paint);
            Path flame = new Path(); flame.moveTo(60, 142); flame.lineTo(65, 156 + 5 * shimmer); flame.lineTo(70, 142); flame.close(); fill(canvas, 0xFFFFD448, flame);
            canvas.restore();
            paint.setColor(0xFFFFF5CA);
            canvas.drawCircle(214, 84 + 2 * wave, 2 + Math.abs(shimmer), paint);
            canvas.drawCircle(285, 94 - 3 * wave, 1.5f + Math.abs(wave), paint);
            if (missionComplete) {
                paint.setColor(0xFFFFE8A0);
                canvas.drawCircle(234, 119 + 2 * wave, 3, paint);
                canvas.drawCircle(276, 145 - 2 * wave, 2.5f, paint);
                canvas.drawCircle(305, 86 + 2 * shimmer, 2.5f, paint);
            }
            canvas.restore();
        }
    }
    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                   checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    private void beginScan() {
        if (!hasPermissions()) {
            if (Build.VERSION.SDK_INT >= 31) requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_PERMISSIONS);
            else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSIONS);
            return;
        }
        if (adapter == null) { showMessage("โทรศัพท์นี้ไม่มี Bluetooth", "This phone has no Bluetooth adapter", true); return; }
        if (!adapter.isEnabled()) {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_BLUETOOTH);
            return;
        }
        scanDevices();
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (hasPermissions()) beginScan();
            else showMessage("ต้องอนุญาตอุปกรณ์ใกล้เคียงเพื่อค้นหาและเชื่อมต่อ BLE", "Allow Nearby devices to scan and connect", true);
        }
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_BLUETOOTH) {
            if (resultCode == RESULT_OK) beginScan();
            else showMessage("กรุณาเปิด Bluetooth ก่อนเริ่มภารกิจ", "Turn on Bluetooth to start the mission", true);
        }
    }
    private void scanDevices() {
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) { showMessage("เปิด Bluetooth แล้วลองค้นหาอีกครั้ง", "Turn on Bluetooth and scan again", true); return; }
        foundDevices.clear();
        foundNames.clear();
        foundMatches.clear();
        foundAddresses.clear();
        deviceListAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<>()) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView item = (TextView) super.getView(position, convertView, parent);
                item.setTextColor(INK);
                item.setTextSize(15);
                item.setPadding(dp(18), dp(12), dp(18), dp(12));
                return item;
            }
        };
        ListView listView = new ListView(this);
        listView.setAdapter(deviceListAdapter);
        scanEmptyLabel = text(tr("กำลังค้นหาอุปกรณ์...\nตรวจว่าอุปกรณ์ของอาจารย์เปิดอยู่", "Searching for devices...\nMake sure the class device is on"), 14, MUTED, false);
        scanEmptyLabel.setGravity(Gravity.CENTER);
        TextView intro = text(tr("เลือกอุปกรณ์ของอาจารย์จากรายการใกล้เคียง", "Select the class device nearby"), 14, INK, false);
        intro.setPadding(dp(18), dp(15), dp(18), dp(9));
        LinearLayout body = column();
        body.setBackgroundColor(CARD);
        body.addView(intro);
        body.addView(listView, lp(-1, 300));
        body.addView(scanEmptyLabel, lp(-1, 300));
        listView.setEmptyView(scanEmptyLabel);
        scanDialog = new AlertDialog.Builder(this)
                .setTitle(tr("ค้นหาอุปกรณ์ BLE", "Scan BLE devices"))
                .setView(body)
                .setNegativeButton(tr("ยกเลิก", "Cancel"), (dialog, which) -> stopScan())
                .create();
        scanDialog.setOnDismissListener(dialog -> stopScan());
        listView.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice selected = foundDevices.get(position);
            String selectedName = foundNames.get(position);
            scanDialog.dismiss();
            connect(selected, selectedName);
        });
        scanDialog.show();
        scanCallback = new ScanCallback() {
            @Override public void onScanResult(int callbackType, ScanResult result) {
                runOnUiThread(() -> {
                    if (!scanning || scanDialog == null || !scanDialog.isShowing()) return;
                    BluetoothDevice device = result.getDevice();
                    String address = device.getAddress();
                    String name = result.getScanRecord() == null ? null : result.getScanRecord().getDeviceName();
                    if (name == null || name.isEmpty()) name = device.getName();
                    boolean matches = result.getScanRecord() != null &&
                            result.getScanRecord().getServiceUuids() != null &&
                            result.getScanRecord().getServiceUuids().contains(new ParcelUuid(SERVICE_UUID));
                    if (!foundAddresses.add(address)) {
                        for (int i = 0; i < foundDevices.size(); i++) {
                            if (address.equals(foundDevices.get(i).getAddress())) {
                                String updatedName = name == null || name.isEmpty() ? foundNames.get(i) : name;
                                boolean updatedMatch = foundMatches.get(i) || matches;
                                if (!updatedName.equals(foundNames.get(i)) || updatedMatch != foundMatches.get(i)) {
                                    foundNames.set(i, updatedName);
                                    foundMatches.set(i, updatedMatch);
                                    deviceListAdapter.remove(deviceListAdapter.getItem(i));
                                    deviceListAdapter.insert(scanItemLabel(updatedName, address, updatedMatch), i);
                                }
                                break;
                            }
                        }
                        return;
                    }
                    if (name == null) name = "";
                    foundDevices.add(device);
                    foundNames.add(name);
                    foundMatches.add(matches);
                    deviceListAdapter.add(scanItemLabel(name, address, matches));
                });
            }
            @Override public void onScanFailed(int errorCode) {
                runOnUiThread(() -> { stopScan(); showMessage("ค้นหา BLE ไม่สำเร็จ (รหัส " + errorCode + ")", "BLE scan failed (code " + errorCode + ")", true); });
            }
        };
        scanning = true;
        try {
            scanner.startScan(null, new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback);
            showMessage("กำลังค้นหาอุปกรณ์ใกล้เคียง...", "Scanning nearby devices...", false);
            scanTimeout = () -> {
                stopScan();
                if (scanDialog != null && scanDialog.isShowing() && foundDevices.isEmpty()) {
                    scanEmptyLabel.setText(tr("ไม่พบอุปกรณ์ BLE\nตรวจว่าอุปกรณ์เปิดอยู่ แล้วค้นหาใหม่", "No BLE devices found\nCheck that the device is on, then scan again"));
                    showMessage("ยังไม่พบอุปกรณ์ ตรวจว่าอุปกรณ์ของอาจารย์เปิดอยู่", "No device found. Check the class device", true);
                }
            };
            handler.postDelayed(scanTimeout, 12000);
        } catch (SecurityException error) {
            stopScan();
            showMessage("ไม่ได้รับสิทธิ์ค้นหา Bluetooth", "Bluetooth scan permission was not granted", true);
        }
    }
    private String scanItemLabel(String name, String address, boolean matches) {
        String title = name.isEmpty() ? tr("อุปกรณ์ไม่มีชื่อ", "Unnamed device") : name;
        return (matches ? tr("★ UUID ตรงโจทย์\n", "★ Assignment UUID match\n") : "") + title + "\n" + address;
    }
    private void stopScan() {
        if (scanTimeout != null) handler.removeCallbacks(scanTimeout);
        scanTimeout = null;
        if (scanning && scanner != null && scanCallback != null) {
            try { scanner.stopScan(scanCallback); } catch (SecurityException ignored) { }
        }
        scanning = false;
        scanCallback = null;
    }
    private void connect(BluetoothDevice device, String scannedName) {
        if (gatt != null) { gatt.close(); gatt = null; }
        busy = true;
        connected = false;
        characteristic = null;
        resetMission();
        deviceName = scannedName.isEmpty() ? device.getAddress() : scannedName;
        showMessage("กำลังเชื่อมต่อ " + deviceName + "...", "Connecting to " + deviceName + "...", false);
        refreshUi();
        try {
            gatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
            if (gatt == null) { busy = false; showMessage("เริ่มเชื่อมต่อ BLE ไม่สำเร็จ", "Could not start BLE connection", true); refreshUi(); }
        }
        catch (SecurityException error) { busy = false; showMessage("ไม่ได้รับสิทธิ์เชื่อมต่อ Bluetooth", "Bluetooth connect permission was not granted", true); refreshUi(); }
    }
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt current, int status, int newState) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                try {
                    if (!current.discoverServices()) runOnUiThread(() -> connectionError("ค้นหา Service ไม่สำเร็จ", "Could not discover BLE services"));
                } catch (SecurityException error) { runOnUiThread(() -> connectionError("ไม่ได้รับสิทธิ์ค้นหา Service", "Service discovery permission was not granted")); }
            } else {
                runOnUiThread(() -> {
                    if (gatt != current) { current.close(); return; }
                    current.close();
                    gatt = null;
                    characteristic = null;
                    connected = false;
                    deviceName = "";
                    busy = false;
                    cancelOperationTimeout();
                    resetMission();
                    showMessage(status == BluetoothGatt.GATT_SUCCESS ? "อุปกรณ์ตัดการเชื่อมต่อ" : "เชื่อมต่อไม่ได้ (รหัส " + status + ")",
                            status == BluetoothGatt.GATT_SUCCESS ? "Device disconnected" : "Connection failed (code " + status + ")", true);
                    refreshUi();
                });
            }
        }
        @Override public void onServicesDiscovered(BluetoothGatt current, int status) {
            runOnUiThread(() -> {
                if (gatt != current) return;
                if (status != BluetoothGatt.GATT_SUCCESS) { connectionError("อ่าน Service ไม่สำเร็จ (รหัส " + status + ")", "Service discovery failed (code " + status + ")"); return; }
                BluetoothGattService service = current.getService(SERVICE_UUID);
                BluetoothGattCharacteristic found = service == null ? null : service.getCharacteristic(CHAR_UUID);
                if (found == null) { connectionError("ไม่พบ Service หรือ Characteristic UUID ตามโจทย์", "Required service or characteristic UUID was not found"); return; }
                int properties = found.getProperties();
                if ((properties & BluetoothGattCharacteristic.PROPERTY_READ) == 0 ||
                    (properties & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) == 0) {
                    connectionError("Characteristic นี้ต้องรองรับทั้งอ่านและเขียน", "Characteristic must support both read and write"); return;
                }
                characteristic = found;
                connected = true;
                busy = false;
                String connectedName = current.getDevice().getName();
                if (connectedName != null && !connectedName.isEmpty()) deviceName = connectedName;
                addLog("เชื่อมต่อ " + deviceName, "Connected to " + deviceName);
                showMessage("เชื่อมต่อสำเร็จ เริ่มด่านที่ 1 ได้เลย", "Connected. Start with the first read", false);
                refreshUi();
            });
        }
        @Override public void onCharacteristicRead(BluetoothGatt current, BluetoothGattCharacteristic item, byte[] value, int status) {
            handleRead(current, item, value, status);
        }
        @SuppressWarnings("deprecation")
        @Override public void onCharacteristicRead(BluetoothGatt current, BluetoothGattCharacteristic item, int status) {
            if (Build.VERSION.SDK_INT < 33) handleRead(current, item, item.getValue(), status);
        }
        @Override public void onCharacteristicWrite(BluetoothGatt current, BluetoothGattCharacteristic item, int status) {
            runOnUiThread(() -> {
                if (gatt != current || phase != 1 || !busy || pendingRead != 0) return;
                cancelOperationTimeout();
                busy = false;
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    phase = 2;
                    addLog("ส่ง: " + lastPayload, "Sent: " + lastPayload);
                    showMessage("ด่านที่ 2 สำเร็จ อ่านผลอีกครั้งได้เลย", "Names sent. Read the result now", false);
                    getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                } else showMessage("ส่งข้อความไม่สำเร็จ (รหัส " + status + ")", "Write failed (code " + status + ")", true);
                refreshUi();
            });
        }
    };
    private void connectionError(String thai, String englishText) {
        if (gatt != null) { gatt.disconnect(); gatt.close(); gatt = null; }
        characteristic = null;
        connected = false;
        busy = false;
        showMessage(thai, englishText, true);
        refreshUi();
    }
    private void read(int number) {
        if (!connected || characteristic == null || busy) return;
        pendingRead = number;
        busy = true;
        refreshUi();
        try {
            if (!gatt.readCharacteristic(characteristic)) operationError("เริ่มอ่านค่าไม่สำเร็จ", "Could not start reading");
            else startOperationTimeout();
        } catch (SecurityException error) { operationError("ไม่ได้รับสิทธิ์อ่านข้อมูล Bluetooth", "Bluetooth read permission was not granted"); }
    }
    private void handleRead(BluetoothGatt current, BluetoothGattCharacteristic item, byte[] bytes, int status) {
        runOnUiThread(() -> {
            if (gatt != current || characteristic != item || pendingRead == 0 || !busy) return;
            int number = pendingRead;
            pendingRead = 0;
            cancelOperationTimeout();
            busy = false;
            if (status != BluetoothGatt.GATT_SUCCESS) { showMessage("อ่านค่าไม่สำเร็จ (รหัส " + status + ")", "Read failed (code " + status + ")", true); refreshUi(); return; }
            String decoded;
            try { decoded = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString(); }
            catch (CharacterCodingException error) { decoded = BINARY_VALUE; }
            if (decoded.isEmpty()) decoded = EMPTY_VALUE;
            String hex = hex(bytes);
            if (number == 1 && phase == 0) {
                firstText = decoded;
                firstHexText = hex + "  •  " + bytes.length + " bytes";
                phase = 1;
                addLog("อ่านครั้งแรก: " + displayValue(decoded, false), "Initial read: " + displayValue(decoded, true));
                showMessage("ด่านที่ 1 สำเร็จ กรอกชื่อแล้วส่ง", "Initial read complete. Enter both names", false);
            } else if (number == 2 && phase == 2) {
                finalText = decoded;
                finalHexText = hex + "  •  " + bytes.length + " bytes";
                phase = 3;
                addLog("ผลจากอุปกรณ์: " + displayValue(decoded, false), "Device result: " + displayValue(decoded, true));
                showMessage("ภารกิจสำเร็จ! แคปหน้าจอส่งงานได้", "Mission complete! Capture your screen", false);
                getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
            refreshUi();
        });
    }
    private String hex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "(ไม่มีข้อมูล)";
        StringBuilder builder = new StringBuilder();
        for (byte value : bytes) builder.append(String.format(Locale.US, "%02X ", value & 0xff));
        return builder.toString().trim();
    }
    private void writeNames() {
        if (!connected || characteristic == null || busy || phase != 1) return;
        String first = yourName.getText().toString().trim();
        String buddy = buddyName.getText().toString().trim();
        String message = payload.getText().toString().trim();
        if (first.isEmpty() || buddy.isEmpty() || message.isEmpty()) { showMessage("กรอกชื่อทั้งสองคนและข้อความที่จะส่ง", "Enter both names and a message", true); return; }
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 512) { showMessage("ข้อความยาวเกิน 512 bytes กรุณาย่อชื่อ", "Message exceeds 512 bytes. Shorten the names", true); return; }
        int props = characteristic.getProperties();
        int writeType = (props & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ?
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT : BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
        lastPayload = message;
        busy = true;
        refreshUi();
        try {
            boolean accepted;
            if (Build.VERSION.SDK_INT >= 33) {
                accepted = gatt.writeCharacteristic(characteristic, bytes, writeType) == BluetoothStatusCodes.SUCCESS;
            } else {
                characteristic.setWriteType(writeType);
                characteristic.setValue(bytes);
                accepted = gatt.writeCharacteristic(characteristic);
            }
            if (!accepted) operationError("เริ่มส่งข้อมูลไม่สำเร็จ", "Could not start writing");
            else startOperationTimeout();
        } catch (SecurityException error) { operationError("ไม่ได้รับสิทธิ์ส่งข้อมูล Bluetooth", "Bluetooth write permission was not granted"); }
    }
    private void operationError(String thai, String englishText) {
        cancelOperationTimeout();
        pendingRead = 0;
        busy = false;
        showMessage(thai, englishText, true);
        refreshUi();
    }
    private void startOperationTimeout() {
        cancelOperationTimeout();
        operationTimeout = () -> operationError("อุปกรณ์ไม่ตอบกลับภายใน 12 วินาที ลองใหม่อีกครั้ง", "No response in 12 seconds. Try again");
        handler.postDelayed(operationTimeout, 12000);
    }
    private void cancelOperationTimeout() {
        if (operationTimeout != null) handler.removeCallbacks(operationTimeout);
        operationTimeout = null;
    }
    private void resetMission() {
        phase = 0;
        pendingRead = 0;
        firstText = "";
        finalText = "";
        firstHexText = "";
        finalHexText = "";
        lastPayload = "";
        logEntries.clear();
        if (firstValue != null) refreshUi();
    }
    @Override protected void onDestroy() {
        stopScan();
        cancelOperationTimeout();
        if (gatt != null) { gatt.close(); gatt = null; }
        super.onDestroy();
    }
}
