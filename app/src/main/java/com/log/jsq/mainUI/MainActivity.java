package com.log.jsq.mainUI;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Vibrator;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.log.jsq.JsqApplication;
import com.log.jsq.aboutUI.AboutActivity;
import com.log.jsq.library.Recorder;
import com.log.jsq.library.Status;
import com.log.jsq.settingUI.SettingActivity;
import com.log.jsq.tool.Audio;
import com.log.jsq.library.FuHao;
import com.log.jsq.R;
import com.log.jsq.historyUI.HistoryListActivity;
import com.log.jsq.tool.AudioOnTTS;
import com.log.jsq.tool.Open;
import com.log.jsq.tool.Theme;

public class MainActivity extends AppCompatActivity implements AudioOnTTS.Exceptional {
    private long mPressedTime = 0;
    public Audio au = null;
    private Vibrator mVibrator = null;
    public final long[] zhenDongTime = {0, 35};
    public final long[] zhenDongTimeLong = {0, 60};
    public final long[] zhenDongTimeAdd = {0, 50, 120, 50};
    private boolean onYuYin = false;
    private boolean onZhenDong = false;
    private boolean onTTS = false;
    private MainUI mainUI;
    public AudioOnTTS tts;
    private Status status;
    private static boolean isCreated = false;
    private TextView mTitle;
    private boolean isExit = false;

    private class StatusWatcher implements Status.Watcher {

        @Override
        public void beforeTypeChanged(int type, int nowType) {}

        @Override
        public void afterTypeChanged(int type, int oldType) {
            switch (oldType) {
                case Status.NORMAL:
                    switch (type) {
                        case Status.EDIT:
                            invalidateOptionsMenu();
                            mTitle.setText(R.string.edit);
                            break;
                    }
                    break;
                case Status.EDIT:
                    switch (type) {
                        case Status.NORMAL:
                            invalidateOptionsMenu();
                            mTitle.setText(R.string.app_name);
                            break;
                    }
                    break;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Theme.setTheme(this);
        setContentView(R.layout.activity_main);

        mainUI = MainUI.getInstance();
        mainUI.init(this);
        status = Status.getInstance();
        status.setWatcher(new StatusWatcher());

        setActionBar();
        loadingSever();
        versionDetection();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isCreated = true;
        recover();
    }

    @Override
    protected void onPause() {
        if (mVibrator != null) {
            mVibrator.cancel();
        }
        if (au != null) {
            au.stopSoundThread();
        }
        if (tts != null) {
            tts.stop();
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseSever(false, false);

        mainUI = null;
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }

        if (isExit) {
            System.exit(0);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();

        switch (status.getType()) {
            case Status.NORMAL:
                inflater.inflate(R.menu.mian_normal_menu, menu);

                final SharedPreferences read = getSharedPreferences("item", MODE_PRIVATE);
                menu.findItem(R.id.zhenDong).setChecked(read.getBoolean("zhenDong", false));
                menu.findItem(R.id.yuYin).setChecked(read.getBoolean("yuYin", false));
                break;
            case Status.EDIT:
                inflater.inflate(R.menu.mian_edit_menu, menu);
                break;
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (status.getType()) {
            case Status.NORMAL:
                if( item.isCheckable() ){
                    final SharedPreferences.Editor editor = getSharedPreferences("item", MODE_PRIVATE).edit();    //存储数据
                    boolean temp = !item.isChecked();
                    boolean _onZhenDong = false;
                    boolean _onYuYin = false;

                    item.setChecked(temp);

                    switch (item.getItemId()) {
                        case R.id.zhenDong:
                            _onZhenDong = temp;
                            _onYuYin = this.onYuYin;
                            editor.putBoolean("zhenDong", temp);
                            break;
                        case R.id.yuYin:
                            _onYuYin = temp;
                            _onZhenDong = this.onZhenDong;
                            editor.putBoolean("yuYin", temp);
                            break;
                    }
                    editor.apply();

                    final boolean onZhenDongFinal = _onZhenDong;
                    final boolean onYuYinFinal = _onYuYin;

                    new Thread() {
                        @Override
                        public void run() {
                            startSever(onZhenDongFinal, onYuYinFinal);
                            releaseSever(onZhenDongFinal, onYuYinFinal);
                            onZhenDong = onZhenDongFinal;
                            onYuYin = onYuYinFinal;
                        }
                    }.start();
                } else {
                    switch (item.getItemId()) {
                        case R.id.edit:
                            status.setType(Status.EDIT);
                            break;
                        case R.id.history:
                            Intent historyIntent =
                                    new Intent(getApplicationContext(), HistoryListActivity.class)
                                    .putExtra("startFrom", getClass().toString())
                                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                            startActivity(historyIntent, new Bundle());
                            break;
                        case R.id.theme:
                            AlertDialog dialog = new AlertDialog.Builder(this)
                                    .setTitle(getString(R.string.theme))
                                    .setPositiveButton(getString(R.string.close),
                                            new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.cancel();
                                        }
                                    })
                                    .create();
                            dialog.setView(getColorPickerView(dialog));
                            dialog.show();
                            break;
                        case R.id.setting:
                            Intent settingIntent =
                                    new Intent(getApplicationContext(), SettingActivity.class)
                                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                            startActivity(settingIntent, new Bundle());
                            break;
                    }
                }
                break;
            case Status.EDIT:
                TextView t1 = findViewById(R.id.textView);
                TextView t2 = findViewById(R.id.textViewNum);
                Recorder.Record record;

                switch (item.getItemId()) {
                    case R.id.done:
                        status.setType(Status.NORMAL);
                        break;
                    case R.id.undo:
                        record = Recorder.undo();
                        if (record != null) {
                            t1.setText(record.equation);
                            t2.setText(record.result);
                        } else {
                            Toast.makeText(this,
                                    R.string.unableUndo,
                                    Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case R.id.redo:
                        record = Recorder.redo();
                        if (record != null) {
                            t1.setText(record.equation);
                            t2.setText(record.result);
                        } else {
                            Toast.makeText(this,
                                    R.string.unableRedo,
                                    Toast.LENGTH_SHORT).show();
                        }
                        break;
                }
        }

        return super.onOptionsItemSelected(item);
    }

    public void zhenDong(long[] t){
        if(onZhenDong) {
            mVibrator.vibrate(t, -1);
        }
    }

    @Override
    public void onBackPressed() {
        long mNowTime = System.currentTimeMillis();

        switch (status.getType()) {
            case Status.NORMAL:
                if (!isMainMod()) {
                    findViewById(R.id.bGuiLing).setVisibility(View.VISIBLE);
                    findViewById(R.id.bShanChu).setVisibility(View.VISIBLE);
                    findViewById(R.id.numsAndFuhaoLayout).setVisibility(View.VISIBLE);

                    TextView textView = findViewById(R.id.textView);
                    textView.setText(textView.getText().toString()
                            .replaceAll("\\s", FuHao.NULL));
                } else if((mNowTime - mPressedTime) > 1000) {
                    Toast.makeText(this, R.string.sureExit, Toast.LENGTH_SHORT)
                            .show();
                    mPressedTime = mNowTime;
                } else {
                    //退出程序
                    final SharedPreferences.Editor editor =
                            getSharedPreferences("list", MODE_PRIVATE).edit();
                    editor.putBoolean("normal", true);
                    editor.putString("textView0", FuHao.NULL);
                    editor.putString("numTextView0", FuHao.NULL);
                    editor.apply();

                    isExit = true;
                    finish();
                }
                break;
            case Status.EDIT:
                status.setType(Status.NORMAL);
                break;
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // 拦截MENU键
        return keyCode == KeyEvent.KEYCODE_MENU || super.onKeyUp(keyCode, event);
    }

    //恢复意外退出之前的内容
    private void recover() {
        mainUI.loadFontSet();
        Theme.setTheme(this);

        new Thread() {
            @Override
            public void run() {
                final SharedPreferences read = getSharedPreferences("list", MODE_PRIVATE);

                if (read.getBoolean("normal", false)) {
                    final SharedPreferences.Editor editor =
                            getSharedPreferences("list", MODE_PRIVATE).edit();
                    editor.putBoolean("normal", false);
                    editor.apply();
                } else {
                    final TextView textView = findViewById(R.id.textView);
                    final TextView textView2 = findViewById(R.id.textView2);
                    final TextView numTextView = findViewById(R.id.textViewNum);
                    final String textViewStr = read.getString("textView0", FuHao.NULL);
                    final String numTextViewStr = read.getString("numTextView0", FuHao.NULL);

                    runOnUiThread(new Runnable() {
                       @Override
                        public void run() {
                            textView.setText(textViewStr);
                            textView2.setText(textView2.getText().toString()
                                    .replaceAll("\\s", FuHao.NULL));
                            numTextView.setText(numTextViewStr);

                           SharedPreferences sp =
                                   getSharedPreferences("setting", MODE_PRIVATE);
                           if (!sp.getBoolean(
                                   "mainActivityHistoryVisibility",
                                   getResources().getBoolean(
                                           R.bool.default_mainActivityVisibilityHistory
                                   ))) {
                               mainUI.setTempHistory(false);
                           }
                       }
                    });

                    // 更新记录者
                    Recorder.update(new Recorder.Record(textViewStr, numTextViewStr));
                }

                startTTS();
            }
        }.start();
    }

    private void loadingSever() {
        new Thread() {
            @Override
            public void run() {
                SharedPreferences sp = getSharedPreferences("item", MODE_PRIVATE);
                boolean _onZhenDong = sp.getBoolean("zhenDong", false);
                boolean _onYuYin = sp.getBoolean("yuYin", false);

                onZhenDong = _onZhenDong;
                onYuYin = _onYuYin;
                startSever(onZhenDong, onYuYin);
            }
        }.start();
    }

    private void startSever(final boolean zhenDong, final boolean yuYin) {
        if (zhenDong && mVibrator == null) {
            mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }

        if (yuYin) {
            if (au == null) {
                au = new Audio(this);
            }

            au.loading();
            startTTS();
        }
    }

    private void releaseSever(final boolean zhenDong, final boolean yuYin) {
        if (!zhenDong && mVibrator != null) {
            mVibrator.cancel();
            mVibrator = null;
        }

        if (!yuYin && au != null) {
            au.stopSoundThread();
            au.release();
            au = null;

            if (tts != null) {
                onTTS = false;
                tts.shutdown();
                tts = null;
            }
        }
    }

    public boolean isOnZhenDong() {
        return onZhenDong;
    }

    public boolean isOnYuYin() {
        return onYuYin;
    }

    public boolean isOnTTS() {
        return onTTS;
    }

    private void restart() {
        Intent intent = getIntent();
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    @SuppressLint("InflateParams")
    private View getColorPickerView(final AlertDialog dialog) {
        final SharedPreferences preferences = getSharedPreferences("item", MODE_PRIVATE);
        LayoutInflater inflater =
                (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View rootView;
        if (inflater != null) {
            rootView = inflater.inflate(R.layout.color_picker, null);
        } else {
            new NullPointerException("inflater is null pointer").printStackTrace();
            return null;
        }

        View.OnClickListener clickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences.Editor spe = preferences.edit();
                switch (v.getId()) {
                    case R.id.itemPurple:
                        spe.putInt("theme", R.style.AppTheme_Purple);
                        break;
                    case R.id.itemGreen:
                        spe.putInt("theme", R.style.AppTheme_Green);
                        break;
                    case R.id.itemDeepOrange:
                        spe.putInt("theme", R.style.AppTheme_DeepOrange);
                        break;
                    case R.id.itemPink:
                        spe.putInt("theme", R.style.AppTheme_Pink);
                        break;
                    case R.id.itemGrey:
                        spe.putInt("theme", R.style.AppTheme_Grey);
                        break;
                    case R.id.itemDeepPurple:
                        spe.putInt("theme", R.style.AppTheme_DeepPurple);
                        break;
                    case R.id.itemIndigo:
                        spe.putInt("theme", R.style.AppTheme_Indigo);
                        break;
                    case R.id.itemTeal:
                        spe.putInt("theme", R.style.AppTheme_Teal);
                        break;
                    case R.id.itemOrange:
                        spe.putInt("theme", R.style.AppTheme_Amber);
                        break;
                    case R.id.itemRed:
                        spe.putInt("theme", R.style.AppTheme_Red);
                        break;
                    case R.id.itemBrown:
                        spe.putInt("theme", R.style.AppTheme_Brown);
                        break;
                    default:
                        spe.putInt("theme", R.style.AppTheme_Blue);
                }

                spe.apply();
                dialog.cancel();
                restart();
            }
        };

        TextView itemPurple = rootView.findViewById(R.id.itemPurple);
        TextView itemBlue = rootView.findViewById(R.id.itemBlue);
        TextView itemGreen = rootView.findViewById(R.id.itemGreen);
        TextView itemDeepOrange = rootView.findViewById(R.id.itemDeepOrange);
        TextView itemPink = rootView.findViewById(R.id.itemPink);
        TextView itemGrey = rootView.findViewById(R.id.itemGrey);
        TextView itemDeepPurple = rootView.findViewById(R.id.itemDeepPurple);
        TextView itemIndigo = rootView.findViewById(R.id.itemIndigo);
        TextView itemTeal = rootView.findViewById(R.id.itemTeal);
        TextView itemAmber = rootView.findViewById(R.id.itemOrange);
        TextView itemRed = rootView.findViewById(R.id.itemRed);
        TextView itemBrown = rootView.findViewById(R.id.itemBrown);

        itemPurple.setOnClickListener(clickListener);
        itemIndigo.setOnClickListener(clickListener);
        itemGreen.setOnClickListener(clickListener);
        itemDeepOrange.setOnClickListener(clickListener);
        itemPink.setOnClickListener(clickListener);
        itemGrey.setOnClickListener(clickListener);
        itemDeepPurple.setOnClickListener(clickListener);
        itemBlue.setOnClickListener(clickListener);
        itemTeal.setOnClickListener(clickListener);
        itemAmber.setOnClickListener(clickListener);
        itemRed.setOnClickListener(clickListener);
        itemBrown.setOnClickListener(clickListener);

        switch (preferences.getInt("theme", 0)) {
            case R.style.AppTheme_Purple:
                itemPurple.setBackground(getDrawable(R.drawable.yuan_double));
                break;
            case R.style.AppTheme_Green:
                itemGreen.setBackground(getDrawable(R.drawable.yuan_double));
                break;
            case R.style.AppTheme_DeepOrange:
                itemDeepOrange.setBackground(getDrawable(R.drawable.yuan_double));
                break;
            case R.style.AppTheme_Pink:
                itemPink.setBackground(getDrawable(R.drawable.yuan_double));
                break;
            case R.style.AppTheme_Grey:
                itemGrey.setBackground(getDrawable(R.drawable.yuan_double));
                break;
            case R.style.AppTheme_DeepPurple:
                itemDeepPurple.setBackground(getDrawable(R.drawable.yuan_double));
                break;
            case R.style.AppTheme_Indigo:
                itemIndigo.setBackground(getDrawable(R.drawable.yuan_double));
                break;
            case R.style.AppTheme_Teal:
                itemTeal.setBackground(getDrawable(R.drawable.yuan_double));
                break;
            case R.style.AppTheme_Amber:
                itemAmber.setBackground(getDrawable(R.drawable.yuan_double));
                break;
            case R.style.AppTheme_Red:
                itemRed.setBackground(getDrawable(R.drawable.yuan_double));
                break;
            case R.style.AppTheme_Brown:
                itemBrown.setBackground(getDrawable(R.drawable.yuan_double));
                break;
            default:
                itemBlue.setBackground(getDrawable(R.drawable.yuan_double));
        }

        return rootView;
    }

    private void versionDetection() {
        if (JsqApplication.newVersion) {
            JsqApplication.newVersion = false;
            final String updateLog = Open.openTxt(getApplicationContext(), R.raw.update_log);

            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.updateLog))
                    .setMessage(updateLog)
                    .setPositiveButton(getString(R.string.exit), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    })
                    .setNegativeButton(getString(R.string.helpWord), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            AboutActivity.openHelp(MainActivity.this);
                        }
                    })
                    .create()
                    .show();
        }
    }

    private void setActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(false);
            actionBar.setDisplayShowCustomEnabled(true);
            actionBar.setTitle(null);
            actionBar.setCustomView(R.layout.main_activity_actionbar);

            mTitle = actionBar.getCustomView().findViewById(R.id.mainActivityTitle);
            mTitle.setOnClickListener(new View.OnClickListener() {
                private long lastTime = 0;
                private final long timeDifference = 500;

                @Override
                public void onClick(View v) {
                    long nowTime = System.currentTimeMillis();

                    if (nowTime - lastTime > timeDifference) {
                        lastTime = nowTime;
                    } else {
                        lastTime = 0;
                        View guiLing = findViewById(R.id.bGuiLing);
                        View shanChu = findViewById(R.id.bShanChu);
                        View linearLayout = findViewById(R.id.numsAndFuhaoLayout);

                        if (isMainMod()) {
                            guiLing.setVisibility(View.GONE);
                            shanChu.setVisibility(View.GONE);
                            linearLayout.setVisibility(View.GONE);
                        } else {
                            guiLing.setVisibility(View.VISIBLE);
                            shanChu.setVisibility(View.VISIBLE);
                            linearLayout.setVisibility(View.VISIBLE);
                        }

                        TextView textView = findViewById(R.id.textView);
                        textView.setText(textView.getText().toString().replaceAll("\\s", FuHao.NULL));
                    }
                }
            });
        }
    }

    public boolean isMainMod() {
        return findViewById(R.id.bGuiLing).getVisibility() == View.VISIBLE
                && findViewById(R.id.bShanChu).getVisibility() == View.VISIBLE
                && findViewById(R.id.numsAndFuhaoLayout).getVisibility() == View.VISIBLE;
    }

    @Override
    public void TTSExceptionalHandle(View view) {
        au.play(view);
    }

    private void startTTS() {
        SharedPreferences sp = getSharedPreferences("setting", MODE_PRIVATE);
        if (sp.getBoolean("onTTS", getResources().getBoolean(R.bool.default_onTTS))){
            String ttsName = sp.getString("setTTSProgram", getString(R.string.default_setTTS_program));

            if (tts != null) {
                onTTS = false;
                tts.shutdown();
                tts = null;
            }

            if (!ttsName.equals(getString(R.string.wu))) {
                tts = new AudioOnTTS(getApplicationContext(), ttsName, this);
                onTTS = true;
            } else{
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(),
                                R.string.selectTTS,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
                onTTS = false;
            }
        } else {
            onTTS = false;
        }
    }

    public static boolean isCreated() {
        return isCreated;
    }
}
