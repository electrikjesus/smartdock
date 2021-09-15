package cu.axel.smartdock.services;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextThemeWrapper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnHoverListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import cu.axel.smartdock.R;
import cu.axel.smartdock.activities.MainActivity;
import cu.axel.smartdock.adapters.AppAdapter;
import cu.axel.smartdock.adapters.AppTaskAdapter;
import cu.axel.smartdock.models.App;
import cu.axel.smartdock.models.AppTask;
import cu.axel.smartdock.services.DockService;
import cu.axel.smartdock.utils.AppUtils;
import cu.axel.smartdock.utils.DeviceUtils;
import cu.axel.smartdock.utils.OnSwipeListener;
import cu.axel.smartdock.utils.Utils;
import cu.axel.smartdock.widgets.HoverInterceptorLayout;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import cu.axel.smartdock.db.DBHelper;
import android.view.inputmethod.InputMethodManager;

public class DockService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener, View.OnTouchListener {

    @Override
    public boolean onTouch(View p1, MotionEvent p2) {
        gestureDetector.onTouchEvent(p2);
        return true;
    }

	private PackageManager pm;
	private SharedPreferences sp;
	private ActivityManager am;
	private ImageView appsBtn,backBtn,homeBtn,recentBtn,splitBtn,powerBtn,wifiBtn,batteryBtn,volBtn,settingsBtn,pinBtn;
    private TextView notificationBtn,searchTv;
	private TextClock dateTv;
	private Button topRightCorner,bottomRightCorner;
	private LinearLayout dockLayout,menu,searchLayout;
	private WindowManager wm;
    private View appsSeparator;
	private boolean menuVisible,shouldHide = true,isPinned,reflectionAllowed;
	private WindowManager.LayoutParams layoutParams;
	private EditText searchEt;
	private ArrayAdapter<App> appAdapter;
	private GridView appsGv,tasksGv,favoritesGv;
	private WifiManager wifiManager;
    private BatteryStatsReceiver batteryReceiver;
    private GestureDetector gestureDetector;
    private DBHelper db;

	@Override
	public void onAccessibilityEvent(AccessibilityEvent p1) {
        if (p1.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            //This might explode
            updateRunningTasks();
        }
    }

	@Override
	public void onInterrupt() {}


	//Handle keyboard shortcuts
	@Override
	protected boolean onKeyEvent(KeyEvent event) {
		if (event.getAction() == KeyEvent.ACTION_UP && event.isAltPressed()) {
			if (event.getKeyCode() == KeyEvent.KEYCODE_L) {
				if (sp.getBoolean("pref_enable_lock_desktop", true))
                    DeviceUtils.	lockScreen(DockService.this);
			} else if (event.getKeyCode() == KeyEvent.KEYCODE_P) {
				if (sp.getBoolean("pref_enable_open_settings", true))
					startActivity(new Intent(Settings.ACTION_SETTINGS));
			} else if (event.getKeyCode() == KeyEvent.KEYCODE_T) {
				if (sp.getBoolean("pref_enable_open_terminal", false)) {
					try {
						startActivity(new Intent(pm.getLaunchIntentForPackage(sp.getString("pref_terminal_package", "com.termux"))));
					} catch (Exception e) {
						Toast.makeText(this, "Oops can't launch terminal. Is it installed ?", 5000).show();
					}
				}

			} else if (event.getKeyCode() == KeyEvent.KEYCODE_A) {
				if (sp.getBoolean("pref_enable_expand_notifications", true))
					performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS);
			} else if (event.getKeyCode() == KeyEvent.KEYCODE_K) {
                DeviceUtils.	sendKeyEvent(KeyEvent.KEYCODE_SYSRQ);
			} else if (event.getKeyCode() == KeyEvent.KEYCODE_M) {
				if (sp.getBoolean("pref_enable_open_music", true))
                    DeviceUtils.	sendKeyEvent(KeyEvent.KEYCODE_MUSIC);
			} else if (event.getKeyCode() == KeyEvent.KEYCODE_B) {
				if (sp.getBoolean("pref_enable_open_browser", true))
                    DeviceUtils.	sendKeyEvent(KeyEvent.KEYCODE_EXPLORER);
			} else if (event.getKeyCode() == KeyEvent.KEYCODE_D) {
				startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
			} else if (event.getKeyCode() == KeyEvent.KEYCODE_O) {
                InputMethodManager im=(InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                im.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
			}
		} else if (event.getAction() == KeyEvent.ACTION_UP) {
			if (event.getKeyCode() == KeyEvent.KEYCODE_CTRL_RIGHT) {
				if (sp.getBoolean("pref_enable_ctrl_back", true)) {
					performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
					return true;
				}
			}

			if (event.getKeyCode() == KeyEvent.KEYCODE_HOME) {
                if (sp.getBoolean("pref_enable_app_menu", true)) {
					toggleMenu(null);
					return true;
				}
			}
            if (event.getKeyCode() == KeyEvent.KEYCODE_F10) {
                if (sp.getBoolean("pref_enable_f10", true))
                    if (shouldHide) { 
                        performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN);
                        return true;
                    }
            }
		}

		return super.onKeyEvent(event);
	}



    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        //Create the dock
        HoverInterceptorLayout dock = (HoverInterceptorLayout) LayoutInflater.from(this).inflate(R.layout.dock, null);
        dockLayout = dock.findViewById(R.id.dock_layout);

        appsBtn = dock.findViewById(R.id.apps_btn);
        tasksGv = dock.findViewById(R.id.apps_lv);

        backBtn = dock.findViewById(R.id.back_btn);
        homeBtn = dock.findViewById(R.id.home_btn);
        recentBtn = dock.findViewById(R.id.recents_btn);
        splitBtn = dock.findViewById(R.id.split_btn);

        notificationBtn = dock.findViewById(R.id.notifications_btn);
        pinBtn = dock.findViewById(R.id.pin_btn);
        wifiBtn = dock.findViewById(R.id.wifi_btn);
        volBtn = dock.findViewById(R.id.volume_btn);
        batteryBtn = dock.findViewById(R.id.battery_btn);
        powerBtn = dock.findViewById(R.id.power_btn);
        dateTv = dock.findViewById(R.id.date_btn);


        dock.setOnHoverListener(new OnHoverListener(){

                @Override
                public boolean onHover(View p1, MotionEvent p2) {
                    if (p2.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                        showDock();
                    } else if (p2.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                        hideDock(500);
                    }

                    return false;
                }


            });
        gestureDetector = new GestureDetector(this, new OnSwipeListener(){
                @Override
                public boolean onSwipe(Direction direction) {
                    if (direction == Direction.up) {
                        showDock();
                        pinDock();
                    }
                    return true;
                }
            });
        dock.setOnTouchListener(this);
        backBtn.setOnClickListener(new OnClickListener(){

                @Override
                public void onClick(View p1) {
                    DockService.this.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                }


            });

        homeBtn.setOnClickListener(new OnClickListener(){

                @Override
                public void onClick(View p1) {
                    DockService.this.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
                }


            });
        recentBtn.setOnClickListener(new OnClickListener(){

                @Override
                public void onClick(View p1) {
                    DockService.this.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
                }


            });

        splitBtn.setOnClickListener(new OnClickListener(){

                @Override
                public void onClick(View p1) {
                    DockService.this.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN);
                }


            });


        tasksGv.setOnItemClickListener(new OnItemClickListener(){

                @Override
                public void onItemClick(AdapterView<?> p1, View p2, int p3, long p4) {
                    AppTask appTask = (AppTask) p1.getItemAtPosition(p3);

                    am.moveTaskToFront(appTask.getId(), 0);
                }


            });


        notificationBtn.setOnClickListener(new OnClickListener(){

                @Override
                public void onClick(View p1) {
                    performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS);
                }
            });
        pinBtn.setOnClickListener(new OnClickListener(){

                @Override
                public void onClick(View p1) {
                    if (isPinned) {
                        unpinDock();

                    } else {
                        pinDock();
                    }
                }


            });
        wifiBtn.setOnLongClickListener(new OnLongClickListener(){

                @Override
                public boolean onLongClick(View p1) {
                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                    return true;
                }
            });

        volBtn.setOnLongClickListener(new OnLongClickListener(){

                @Override
                public boolean onLongClick(View p1) {
                    startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS));
                    return true;
                }
            });

        powerBtn.setOnClickListener(new OnClickListener(){

                @Override
                public void onClick(View p1) {
                    DockService.this.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG);
                }


            });

        dateTv.setOnClickListener(new OnClickListener(){

                @Override
                public void onClick(View p1) {
                    startActivity(pm.getLaunchIntentForPackage("com.android.deskclock"));
                }


            });
        dateTv.setOnLongClickListener(new OnLongClickListener(){

                @Override
                public boolean onLongClick(View p1) {
                    startActivity(new Intent(Settings.ACTION_DATE_SETTINGS));
                    return true;
                }
            });


        layoutParams = new WindowManager.LayoutParams();
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT; 
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.gravity = Gravity.BOTTOM;
        layoutParams.format = PixelFormat.TRANSLUCENT;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |  WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else
            layoutParams.type = WindowManager.LayoutParams.TYPE_PHONE;

        wm.addView(dock, layoutParams);

        //Hot corners
        topRightCorner = new Button(this);
        topRightCorner.setBackgroundResource(R.drawable.corner_background);

        bottomRightCorner = new Button(this);
        bottomRightCorner.setBackgroundResource(R.drawable.corner_background);


        topRightCorner.setOnHoverListener(new OnHoverListener(){

                @Override
                public boolean onHover(View p1, MotionEvent p2) {
                    if (p2.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                        Handler handler=new Handler();
                        handler.postDelayed(
                            new Runnable(){

                                @Override
                                public void run() {
                                    if (topRightCorner.isHovered())
                                        performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
                                }


                            }, Integer.parseInt(sp.getString("pref_hot_corners_delay", "300")));

                    }

                    return false;
                }


            });

        bottomRightCorner.setOnHoverListener(new OnHoverListener(){

                @Override
                public boolean onHover(View p1, MotionEvent p2) {
                    if (p2.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                        Handler handler=new Handler();
                        handler.postDelayed(
                            new Runnable(){

                                @Override
                                public void run() {
                                    if (bottomRightCorner.isHovered())
                                        DeviceUtils. lockScreen(DockService.this);
                                }


                            }, Integer.parseInt(sp.getString("pref_hot_corners_delay", "300")));

                    }
                    return false;
                }


            });

        updateCorners();

        layoutParams.width = 2;
        layoutParams.gravity = Gravity.TOP | Gravity.RIGHT;
        wm.addView(topRightCorner, layoutParams);

        layoutParams.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        wm.addView(bottomRightCorner, layoutParams);


        //App menu
        layoutParams.flags =  WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        layoutParams.gravity = Gravity.BOTTOM | Gravity.LEFT;

        menu = (LinearLayout) LayoutInflater.from(this).inflate(R.layout.apps_menu, null);
        searchEt = menu.findViewById(R.id.menu_et);
        appsGv = menu.findViewById(R.id.menu_applist_lv);
        favoritesGv = menu.findViewById(R.id.fav_applist_lv);
        settingsBtn = menu.findViewById(R.id.settings_btn);
        searchLayout = menu.findViewById(R.id.search_layout);
        searchTv = menu.findViewById(R.id.search_tv);
        appsSeparator = menu.findViewById(R.id.apps_separator);


        appsGv.setOnItemClickListener(new OnItemClickListener(){

                @Override
                public void onItemClick(AdapterView<?> p1, View p2, int p3, long p4) {
                    App app = (App) p1.getItemAtPosition(p3);
                    launchApp(null, app.getPackagename());
                }


            });

        appsGv.setOnItemLongClickListener(new OnItemLongClickListener(){

                @Override
                public boolean onItemLongClick(AdapterView<?> p1, View p2, int p3, long p4) {
                    final App app =(App) p1.getItemAtPosition(p3);
                    PopupMenu pmenu=new PopupMenu(new ContextThemeWrapper(DockService.this, R.style.PopupMenuTheme), p2);

                    Utils.setForceShowIcon(pmenu);

                    pmenu.inflate(R.menu.app_menu);

                    if (AppUtils.isPinned(app.getPackagename())) {
                        pmenu.getMenu().add(0, 4, 0, "Unpin").setIcon(R.drawable.ic_unpin);
                    } else {
                        pmenu.getMenu().add(0, 3, 0, "Pin").setIcon(R.drawable.ic_pin);
                    }


                    pmenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener(){

                            @Override
                            public boolean onMenuItemClick(MenuItem p1) {
                                switch (p1.getItemId()) {
                                    case R.id.action_appinfo:
                                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:" + app.getPackagename())));
                                        hideMenu();
                                        break;
                                    case R.id.action_uninstall:
                                        startActivity(new Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:" + app.getPackagename())));
                                        hideMenu();
                                        break;
                                    case 3:
                                        AppUtils.pinApp(app.getPackagename());
                                        loadFavoriteApps();
                                        break;
                                    case 4:
                                        AppUtils.unpinApp(app.getPackagename());
                                        loadFavoriteApps();
                                        break;
                                    case R.id.action_launch_standard:
                                        launchApp("standard", app.getPackagename());
                                        break;
                                    case R.id.action_launch_maximized:
                                        launchApp("maximized", app.getPackagename());
                                        break;
                                    case R.id.action_launch_portrait:
                                        launchApp("portrait", app.getPackagename());
                                        break;
                                    case R.id.action_launch_fullscreen:
                                        launchApp("fullscreen", app.getPackagename());
                                        break;
                                }
                                return false;
                            }

                        });

                    pmenu.show();
                    return true;
                }
            });

        favoritesGv.setOnItemClickListener(new OnItemClickListener(){

                @Override
                public void onItemClick(AdapterView<?> p1, View p2, int p3, long p4) {
                    App app = (App) p1.getItemAtPosition(p3);
                    launchApp(null, app.getPackagename());
                }


            });

        favoritesGv.setOnItemLongClickListener(new OnItemLongClickListener(){

                @Override
                public boolean onItemLongClick(AdapterView<?> p1, View p2, int p3, long p4) {
                    final App app =(App) p1.getItemAtPosition(p3);
                    PopupMenu pmenu=new PopupMenu(new ContextThemeWrapper(DockService.this, R.style.PopupMenuTheme), p2);

                    Utils.setForceShowIcon(pmenu);

                    pmenu.inflate(R.menu.app_menu);
                    pmenu.getMenu().add(0, 4, 0, "Unpin").setIcon(R.drawable.ic_unpin);


                    pmenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener(){

                            @Override
                            public boolean onMenuItemClick(MenuItem p1) {
                                switch (p1.getItemId()) {
                                    case R.id.action_appinfo:
                                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:" + app.getPackagename())));
                                        hideMenu();
                                        break;
                                    case R.id.action_uninstall:
                                        startActivity(new Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:" + app.getPackagename())));
                                        hideMenu();
                                        break;
                                    case 4:
                                        AppUtils.unpinApp(app.getPackagename());
                                        loadFavoriteApps();
                                        break;
                                    case R.id.action_launch_standard:
                                        launchApp("standard", app.getPackagename());
                                        break;
                                    case R.id.action_launch_maximized:
                                        launchApp("maximized", app.getPackagename());
                                        break;
                                    case R.id.action_launch_portrait:
                                        launchApp("portrait", app.getPackagename());
                                        break;
                                    case R.id.action_launch_fullscreen:
                                        launchApp("fullscreen", app.getPackagename());
                                        break;
                                }
                                return false;
                            }
                        });

                    pmenu.show();
                    return true;
                }
            });

        searchTv.setOnClickListener(new OnClickListener(){

                @Override
                public void onClick(View p1) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + URLEncoder.encode(searchEt.getText().toString()))));
                    hideMenu();
                }
            });

        searchEt.addTextChangedListener(new TextWatcher(){

                @Override
                public void beforeTextChanged(CharSequence p1, int p2, int p3, int p4) {}

                @Override
                public void onTextChanged(CharSequence p1, int p2, int p3, int p4) {}

                @Override
                public void afterTextChanged(Editable p1) {
                    if (appAdapter != null)
                        appAdapter.getFilter().filter(p1.toString());
                    if (p1.length() > 0) {
                        searchLayout.setVisibility(View.VISIBLE);
                        searchTv.setText("Search for \"" + p1.toString() + "\" on Google");
                        hideFavorites();
                    } else {
                        searchLayout.setVisibility(View.GONE);
                        if (AppUtils.getFavoriteApps(pm).size() > 0)
                            showFavorites();

                    }
                }

            });

        new UpdateAppMenuTask().execute();

        loadFavoriteApps();

        //TODO: Filter app menu click
        menu.setOnTouchListener(new OnTouchListener(){

                @Override
                public boolean onTouch(View p1, MotionEvent p2) {
                    if (p2.getAction() == p2.ACTION_OUTSIDE) {
                        hideMenu();   
                    }
                    return false;
                }
            });

        updateNavigationBar();
        applyTheme();
        updateMenuIcon();

        //Watch for launcher visibility
        registerReceiver(new BroadcastReceiver(){

                @Override
                public void onReceive(Context p1, Intent p2) {
                    switch (p2.getStringExtra("action")) {
                        case "pause":
                            shouldHide = true;
                            hideDock(500);
                            break;
                        case "resume":
                            shouldHide = false;
                            showDock();
                    }
                }
            }, new IntentFilter(getPackageName() + ".HOME"){});

        //Tell the launcher the service has connected
        sendBroadcast(new Intent(getPackageName() + ".CONNECTED"));

        registerReceiver(new BroadcastReceiver(){

                @Override
                public void onReceive(Context p1, Intent p2) {
                    int count = p2.getIntExtra("count", 0);
                    notificationBtn.setText(count + "");
                }
            }, new IntentFilter(getPackageName() + ".NOTIFICATION_COUNT_CHANGED"));

        batteryReceiver = new BatteryStatsReceiver();

        registerReceiver(batteryReceiver, new IntentFilter("android.intent.action.BATTERY_CHANGED"));

        //Run startup script
        Utils.doAutostart();

        showDock();
        hideDock(2000);

    }

	@Override
	public void onCreate() {
		super.onCreate();

		Toast.makeText(this, "Smart Dock started", 5000).show();

        reflectionAllowed = Build.VERSION.SDK_INT < 28 || Utils.allowReflection();

        db = new DBHelper(this);
		pm = getPackageManager();
		am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		sp = PreferenceManager.getDefaultSharedPreferences(this);
		sp.registerOnSharedPreferenceChangeListener(this);
		wm = (WindowManager) getSystemService(WINDOW_SERVICE);
		wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
	}

	public void showDock() {
		updateRunningTasks();
		dockLayout.setVisibility(View.VISIBLE);
	}

    public void pinDock() {
        isPinned = true;
        pinBtn.setImageResource(R.drawable.ic_pin);

    }
    private void unpinDock() {
        pinBtn.setImageResource(R.drawable.ic_unpin);
        isPinned = false;
        hideDock(500);
    }

    private void launchApp(String mode, String packagename) {
        if (mode == null) {
            String m;
            if (sp.getBoolean("pref_remember_launch_mode", true) && (m = db.getLaunchMode(packagename)) != null)
                mode = m;
            else if (AppUtils.isGame(pm, packagename))
                mode = "fullscreen";
            else
                mode = sp.getString("pref_launch_mode", "standard");
        } else {
            if (sp.getBoolean("pref_remember_launch_mode", true))
                db.saveLaunchMode(packagename, mode);
        }
        launchApp(mode, pm.getLaunchIntentForPackage(packagename));
    }
    private void launchApp(String mode, Intent intent) {
        if (Build.VERSION.SDK_INT < 24) {
            try {
                startActivity(intent);
            } catch (Exception e) {}
            return;
        }
        ActivityOptions options=ActivityOptions.makeBasic();
        try {
            if (!reflectionAllowed) Utils.allowReflection();
            String methodName = Build.VERSION.SDK_INT >= 28 ?"setLaunchWindowingMode": "setLaunchStackId";
            int windowMode;
            if (mode.equals("fullscreen")) {
                windowMode = 1;    
            } else {
                int width=0,height=0,x=0,y=0;
                int deviceWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
                int deviceHeight  = Resources.getSystem().getDisplayMetrics().heightPixels;

                windowMode = Build.VERSION.SDK_INT >= 28 ?5: 2;
                if (mode.equals("standard")) {
                    x = deviceWidth / 5;
                    y = deviceHeight / 6;
                    width = deviceWidth - x;
                    height = deviceHeight - y;

                } else if (mode.equals("maximized")) {
                    width = deviceWidth;
                    height = deviceHeight - (dockLayout.getMeasuredHeight() + getStatusBarHeight());
                } else if (mode.equals("portrait")) {
                    x = deviceWidth / 3;
                    y = deviceHeight / 15;
                    width = deviceWidth - x;
                    height = deviceHeight - y;
                }
                options.setLaunchBounds(new Rect(x, y, width, height));
            }

            Method method=ActivityOptions.class.getMethod(methodName, int.class);
            method.invoke(options, windowMode);
            startActivity(intent, options.toBundle());
            hideMenu();
            if (!mode.equals("fullscreen")) {
                showDock();
                pinDock();   
            } else
                unpinDock();
        } catch (Exception e) {
            Toast.makeText(DockService.this, e.toString(), 5000).show();
        }

    }

    public int getStatusBarHeight() { 
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        } 
        return result;
    }

	public void hideDock(int delay) {
        new Handler().postDelayed(new Runnable(){

                @Override
                public void run() {
                    if (shouldHide && !isPinned)
                        dockLayout.setVisibility(View.GONE);

                }
            }, delay);}


    public void openDockSettings(View v) {
        startActivity(new Intent(this, MainActivity.class));
        hideMenu();
	}

	public void toggleMenu(View v) {
		if (menuVisible) {
			hideMenu();
		} else {
			showMenu();

		}

	}

	public void showMenu() {
        layoutParams.width = Utils.dpToPx(this, Integer.parseInt(sp.getString("pref_app_menu_width", "650")));
        layoutParams.height = Utils.dpToPx(this, Integer.parseInt(sp.getString("pref_app_menu_height", "540")));
        layoutParams.x = Utils.dpToPx(this, Integer.parseInt(sp.getString("pref_app_menu_x", "2")));
		layoutParams.y = Utils.dpToPx(this, Integer.parseInt(sp.getString("pref_app_menu_y", "60")));
		wm.addView(menu, layoutParams);
		new UpdateAppMenuTask().execute();
		menu.setAlpha(0);
		menu.animate()
			.alpha(1)
			.setDuration(200)
			.setInterpolator(new AccelerateDecelerateInterpolator());

		menuVisible = true;

	}

	public void hideMenu() {
		searchEt.setText("");
		wm.removeView(menu);
		menuVisible = false;
	}	

	@Override
	public void onSharedPreferenceChanged(SharedPreferences p1, String p2) {
        if (p2.equals("pref_theme"))
            applyTheme();
        else if (p2.equals("pref_menu_icon_uri"))
            updateMenuIcon();
        else {
            updateNavigationBar();
            updateCorners();   
        }
	}

    public void updateRunningTasks() {
        List<ActivityManager.RunningTaskInfo> tasksInfo = am.getRunningTasks(20);

        ArrayList<AppTask> appTasks = new ArrayList<AppTask>();

        for (ActivityManager.RunningTaskInfo taskInfo : tasksInfo) {
            try {
                //Exclude systemui, launcher and other system apps from the tasklist
                if (taskInfo.baseActivity.getPackageName().contains("com.android.systemui") 
                    || taskInfo.baseActivity.getPackageName().contains("com.google.android.packageinstaller"))
                    continue;

                if (taskInfo.topActivity.getClassName().equals(getPackageName() + ".activities.LauncherActivity")) {
                    continue;
                }

                appTasks.add(new AppTask(taskInfo.id, taskInfo.topActivity.getShortClassName(), taskInfo.topActivity.getPackageName(), pm.getActivityIcon(taskInfo.topActivity)));
            } catch (PackageManager.NameNotFoundException e) {}
        }

        tasksGv.setAdapter(new AppTaskAdapter(DockService.this, appTasks));

        if (wifiManager.isWifiEnabled()) {
            wifiBtn.setImageResource(R.drawable.ic_wifi_on);
        } else {
            wifiBtn.setImageResource(R.drawable.ic_wifi_off);
        }

    }
    public void updateNavigationBar() {
        if (sp.getBoolean("pref_enable_back", false)) {
            backBtn.setVisibility(View.VISIBLE);
        } else {
            backBtn.setVisibility(View.GONE);
        }
        if (sp.getBoolean("pref_enable_home", false)) {
            homeBtn.setVisibility(View.VISIBLE);
        } else {
            homeBtn.setVisibility(View.GONE);
        }
        if (sp.getBoolean("pref_enable_recents", false)) {
            recentBtn.setVisibility(View.VISIBLE);
        } else {
            recentBtn.setVisibility(View.GONE);
        }
        if (sp.getBoolean("pref_enable_split", false)) {
            splitBtn.setVisibility(View.VISIBLE);
        } else {
            splitBtn.setVisibility(View.GONE);
        }


	}

	public void toggleWifi(View v) {
        boolean enabled = wifiManager.isWifiEnabled();
        int icon= !enabled ?R.drawable.ic_wifi_on: R.drawable.ic_wifi_off;
        wifiBtn.setImageResource(icon);
        wifiManager.setWifiEnabled(!enabled);

	}

    public void toggleVolume(View v) {
        DeviceUtils.toggleVolume(this);
	}

    public void applyTheme() {
        switch (sp.getString("pref_theme", "dark")) {
            case "pref_theme_dark":
                dockLayout.setBackgroundResource(R.drawable.round_rect_solid_dark);
                menu.setBackgroundResource(R.drawable.round_rect_solid_dark);
                searchEt.setBackgroundResource(R.drawable.search_background_dark);
                pinBtn.setBackgroundResource(R.drawable.circle_solid_dark);
                wifiBtn.setBackgroundResource(R.drawable.circle_solid_dark);
                volBtn.setBackgroundResource(R.drawable.circle_solid_dark);
                powerBtn.setBackgroundResource(R.drawable.circle_solid_dark);
                batteryBtn.setBackgroundResource(R.drawable.circle_solid_dark);
                settingsBtn.setBackgroundResource(R.drawable.circle_solid_dark);
                backBtn.setBackgroundResource(R.drawable.circle_solid_dark);
                homeBtn.setBackgroundResource(R.drawable.circle_solid_dark);
                recentBtn.setBackgroundResource(R.drawable.circle_solid_dark);
                splitBtn.setBackgroundResource(R.drawable.circle_solid_dark);
                break;

            case "pref_theme_black":
                dockLayout.setBackgroundResource(R.drawable.round_rect_solid_black);
                menu.setBackgroundResource(R.drawable.round_rect_solid_black);
                searchEt.setBackgroundResource(R.drawable.search_background_black);
                pinBtn.setBackgroundResource(R.drawable.circle_solid_black);
                wifiBtn.setBackgroundResource(R.drawable.circle_solid_black);
                volBtn.setBackgroundResource(R.drawable.circle_solid_black);
                powerBtn.setBackgroundResource(R.drawable.circle_solid_black);
                batteryBtn.setBackgroundResource(R.drawable.circle_solid_black);
                settingsBtn.setBackgroundResource(R.drawable.circle_solid_black);
                backBtn.setBackgroundResource(R.drawable.circle_solid_black);
                homeBtn.setBackgroundResource(R.drawable.circle_solid_black);
                recentBtn.setBackgroundResource(R.drawable.circle_solid_black);
                splitBtn.setBackgroundResource(R.drawable.circle_solid_black);

                break;
            case "pref_theme_transparent":
                dockLayout.setBackgroundResource(R.drawable.round_rect_transparent);
                menu.setBackgroundResource(R.drawable.round_rect_transparent);
                searchEt.setBackgroundResource(R.drawable.search_background_transparent);
                pinBtn.setBackgroundResource(R.drawable.circle_transparent);
                wifiBtn.setBackgroundResource(R.drawable.circle_transparent);
                volBtn.setBackgroundResource(R.drawable.circle_transparent);
                powerBtn.setBackgroundResource(R.drawable.circle_transparent);
                batteryBtn.setBackgroundResource(R.drawable.circle_transparent);
                settingsBtn.setBackgroundResource(R.drawable.circle_transparent);
                backBtn.setBackgroundResource(R.drawable.circle_transparent);
                homeBtn.setBackgroundResource(R.drawable.circle_transparent);
                recentBtn.setBackgroundResource(R.drawable.circle_transparent);
                splitBtn.setBackgroundResource(R.drawable.circle_transparent);
                break;


        }
    }

	public void updateCorners() {
		if (sp.getBoolean("pref_enable_top_right", false)) {
			topRightCorner.setVisibility(View.VISIBLE);
		} else {
			topRightCorner.setVisibility(View.GONE);
		}

		if (sp.getBoolean("pref_enable_bottom_right", false)) {
			bottomRightCorner.setVisibility(View.VISIBLE);
		} else {
			bottomRightCorner.setVisibility(View.GONE);
		}

	}

    public void updateMenuIcon() {
        String iconUri=sp.getString("pref_menu_icon_uri", "default");
        if (iconUri.equals("default")) {
            appsBtn.setImageResource(R.drawable.ic_apps);
        } else {
            Uri icon = Uri.parse(iconUri);
            if (icon != null)
                appsBtn.setImageURI(icon);
        }

    }

    public void hideFavorites() {
        favoritesGv.setVisibility(View.GONE);
        appsSeparator.setVisibility(View.GONE);
    }

    public void showFavorites() {
        favoritesGv.setVisibility(View.VISIBLE);
        appsSeparator.setVisibility(View.VISIBLE);
    }

    public void loadFavoriteApps() {
        ArrayList<App> apps = AppUtils.getFavoriteApps(pm);
        if (apps.size() > 0) {
            showFavorites();
        } else {
            hideFavorites();
        }
        favoritesGv.setAdapter(new AppAdapter(this, apps));

    }

    @Override
    public void onDestroy() {
        //TODO: Unregister all receivers
        sp.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
	}

	class UpdateAppMenuTask extends AsyncTask<Void, Void, ArrayList<App>> {
		@Override
		protected ArrayList<App> doInBackground(Void[] p1) {
			return AppUtils.getInstalledApps(pm);
		}

		@Override
		protected void onPostExecute(ArrayList<App> result) {
			super.onPostExecute(result);

            //TODO: Fix this shit
            appAdapter = new AppAdapter(DockService.this, result);
            appsGv.setAdapter(appAdapter);

			/*if (appAdapter == null)
             {

             }
             else
             {
             appAdapter.clear();
             appAdapter.addAll(result);
             appsGv.setAdapter(appAdapter);
             }*/
		}


	}
    class BatteryStatsReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context p1, Intent intent) {
            int level = intent.getExtras().getInt("level");

            if (intent.getExtras().getInt("plugged") == 0) {
                if (level == 0)
                    batteryBtn.setImageResource(R.drawable.battery_empty);
                else if (level > 0 && level < 30)
                    batteryBtn.setImageResource(R.drawable.battery_20);
                else if (level > 30 && level < 50)
                    batteryBtn.setImageResource(R.drawable.battery_30);
                else if (level > 50 && level < 60)
                    batteryBtn.setImageResource(R.drawable.battery_50);
                else if (level > 60 && level < 80)
                    batteryBtn.setImageResource(R.drawable.battery_60);
                else if (level > 80 && level < 90)
                    batteryBtn.setImageResource(R.drawable.battery_80);
                else if (level > 90 && level < 100)
                    batteryBtn.setImageResource(R.drawable.battery_90);
                else if (level == 100)
                    batteryBtn.setImageResource(R.drawable.battery_full);
            } else {
                if (level == 0)
                    batteryBtn.setImageResource(R.drawable.battery_charging_empty);
                else if (level > 0 && level < 30)
                    batteryBtn.setImageResource(R.drawable.battery_charging_20);
                else if (level > 30 && level < 50)
                    batteryBtn.setImageResource(R.drawable.battery_charging_30);
                else if (level > 50 && level < 60)
                    batteryBtn.setImageResource(R.drawable.battery_charging_50);
                else if (level > 60 && level < 80)
                    batteryBtn.setImageResource(R.drawable.battery_charging_60);
                else if (level > 80 && level < 90)
                    batteryBtn.setImageResource(R.drawable.battery_charging_80);
                else if (level > 90 && level < 100)
                    batteryBtn.setImageResource(R.drawable.battery_charging_90);
                else if (level == 100)
                    batteryBtn.setImageResource(R.drawable.battery_charging_full);
            }
        }


    }
}
