package com.beatsportable.beats;

import java.io.File;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.Bitmap.Config;
import android.graphics.Region.Op;
import android.media.AudioManager;
import android.os.*;
import android.util.Log;
import android.util.SparseArray;
import android.view.*;
import android.os.Vibrator;

import android.R.*;
import android.app.Activity;
import android.bluetooth.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.*;

import zephyr.android.BioHarnessBT.*;

/*--------------- DB includes ---------------*/

import android.database.sqlite.SQLiteDatabase;

/*-------------------------------------------*/

public class GUIGame extends Activity {
	
	// TODO - MAJOR CLEANUP!!!
	
	private String title = "";
	private GUIHandler h;
	private DataParser dp;
	private MusicService mp;
	private GUIListeners listeners;
	
	private GameViewHandler mView;
	private int orientation;
	private boolean backgroundShow;
	private boolean backgroundSong;
	private boolean backgroundFiltering;
	private int backgroundBrightness;
	private int frame_millis;
	private double speed_multiplier;
	private int noteAppearance;
	private int randomize;
	private boolean osu;
	private boolean dark;
	private boolean jumps;
	private boolean holds;
	private boolean autoPlay;
	private boolean showFPS;
	private boolean screenshotMode;
	private boolean fullscreen;
	private boolean debugTime;
	private int syncDuration;
	private int manualOffset;
	private int manualOGGOffset;
    NewConnectedListener _NConnListener;
    private final int RESPIRATION_RATE = 0x101;
    public String respRate = "000";
    String rightSettingsTop;

	private static final int ROTATABLE = 2;

    /*-------these variables are needed for breath display -gp ----------*/

    private boolean showBreath; //default True to show
    private int increment = 0; //start at zero
    private int breathBase = 0; //start at zero
    private int interval = 25; //default .04 sec interval
    private int breathGoal; //default 0 (6/4 option)
    private int inBreath; //default 4 sec
    private int outBreath; //default 6 sec
    private int span; //default 10 sec
    private int breathColor; //default 0 (none), 1 (red), 2 (green), 3 (blue)
    private int flag = 0;

    /*--------------------------------------------------------------------*/

	private GUIDrawingArea drawarea = new GUIDrawingArea() {
		
		private SparseArray<Bitmap> rsrcBitmaps = new SparseArray<Bitmap>();
		@Override
		public Bitmap getBitmap(String rsrc, int width, int height) {
			Bitmap b = rsrcBitmaps.get(rsrc.hashCode());
			if (b == null) {
				String path = Tools.getNoteSkinsDir() + rsrc;
				Bitmap loaded = BitmapFactory.decodeFile(path);
				if (loaded == null) {
					ToolsTracker.info("Unable to load graphics: " + path);
					path = Tools.getNoteSkinsDirDefault() + rsrc;
					loaded = BitmapFactory.decodeFile(path);
				}
				if (loaded == null) {
					ToolsTracker.info("Unable to load graphics: " + path);
					loaded = BitmapFactory.decodeResource(getResources(), R.drawable.missing_graphics);
				}
				b = Bitmap.createScaledBitmap(
						loaded,
						width, height,
						true
						);
				rsrcBitmaps.put(rsrc.hashCode(), b);
			}
			return b;
		}
		@Override
		public void clearBitmaps() {
			//grr garbage collector fail
			for (int i=0; i<rsrcBitmaps.size(); i++) {
				rsrcBitmaps.valueAt(i).recycle();
			}
			rsrcBitmaps.clear();
		}
		
		@Override
		public int pitchToX(int pitch) { return h.pitchToX(pitch); }
		@Override
		public int timeToY(float time) { return h.timeToY(time); }	
		
		@Override
		public void setClip_screen(Canvas canvas) {
			//canvas.clipRect(0, 0, canvas.getWidth(), canvas.getHeight(), Op.REPLACE);
			canvas.clipRect(0, 0, Tools.screen_w, Tools.screen_h, Op.REPLACE);
		}
		@Override
		public void setClip_arrowSpace(Canvas canvas) {
			/* Note: if we need midpt to depend on button locations or something,
			 * we can move the logic of this method to GUIHandler
			 * (in the same manner as timeToY and pitchToX) */
			boolean fallingDown = false;
			switch (Tools.gameMode) {
				case Tools.STANDARD: fallingDown = false;    break;
				case Tools.REVERSE:  fallingDown = true;     break;
				case Tools.OSU_MOD:  setClip_screen(canvas); return;
			}
			int ymin, ymax, midpt;
			switch (noteAppearance) {
			case 1: //Hidden (appear, then disappear)
				midpt = Tools.screen_h / 2 - Tools.button_h;
				ymin = fallingDown ? 0     : (Tools.screen_h - midpt);
				ymax = fallingDown ? midpt : Tools.screen_h;
				break;
			case 2: //Sudden (appear very late)
				midpt = Tools.screen_h / 2 - Tools.button_h;
				ymin = fallingDown ? midpt          : 0;
				ymax = fallingDown ? Tools.screen_h : (Tools.screen_h - midpt);
				break;
			case 3: // Invisible (never appear)
				ymin = -1;
				ymax = -1;
				break;
			case 0: default: //Visible (normal)
				ymin = 0;
				ymax = Tools.screen_h;
				break;
			}
			canvas.clipRect(0, ymin, Tools.screen_w, ymax, Op.REPLACE);
		}
	};


@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Tools.setContext(this);
		System.gc(); // Request for garbage collection and hope it runs before updating starts
		// Get settings
		/*
		int priority = Integer.valueOf(
				Tools.getSetting(R.string.appPriority, R.string.appPriorityDefault));
				*/
		boolean additionalVibrations =
				Tools.getBooleanSetting(R.string.additionalVibrations, R.string.additionalVibrationsDefault);
		orientation = Integer.valueOf(
				Tools.getSetting(R.string.orientation, R.string.orientationDefault));
		backgroundShow =
				Tools.getBooleanSetting(R.string.backgroundShow, R.string.backgroundShowDefault);
		backgroundSong =
			Tools.getBooleanSetting(R.string.backgroundSong, R.string.backgroundSongDefault);
		backgroundFiltering =
				Tools.getBooleanSetting(R.string.backgroundFiltering, R.string.backgroundFilteringDefault);
		backgroundBrightness = Integer.valueOf(
				Tools.getSetting(R.string.backgroundBrightness, R.string.backgroundBrightnessDefault));
		//frame_millis = Integer.valueOf(
		//		Tools.getSetting(R.string.fps, R.string.fpsDefault));
		frame_millis = 17; // 60 FPS = 1000/17
		speed_multiplier = Double.valueOf(
				Tools.getSetting(R.string.speedMultiplier, R.string.speedMultiplierDefault));
		noteAppearance = Integer.valueOf(
				Tools.getSetting(R.string.noteAppearance, R.string.noteAppearanceDefault));
		randomize = Integer.parseInt(
				Tools.getSetting(R.string.randomize, R.string.randomizeDefault));
		osu = (Tools.gameMode == Tools.OSU_MOD);
		dark = Tools.getBooleanSetting(R.string.dark, R.string.darkDefault);
		jumps = Tools.getBooleanSetting(R.string.jumps, R.string.jumpsDefault);
		holds = Tools.getBooleanSetting(R.string.holds, R.string.holdsDefault);
		autoPlay = Tools.getBooleanSetting(R.string.autoPlay, R.string.autoPlayDefault);
		showFPS = Tools.getBooleanSetting(R.string.showFPS, R.string.showFPSDefault);
        showBreath = Tools.getBooleanSetting(R.string.showBreath, R.string.showBreathDefault);
        breathColor = Integer.valueOf(
            Tools.getSetting(R.string.breathColor, R.string.breathColorDefault));
        breathGoal = Integer.valueOf(
            Tools.getSetting(R.string.breathGoal, R.string.breathGoalDefault));
// --------------------- ADDED -----------------------------------------
        //showBPM = Tools.getBooleanSetting("showBPM", "1");
		screenshotMode = Tools.getBooleanSetting(R.string.screenshotMode, R.string.screenshotModeDefault);
		fullscreen = Tools.getBooleanSetting(R.string.fullscreen, R.string.fullscreenDefault);
		debugTime = Tools.getBooleanSetting(R.string.debugTime, R.string.debugTimeDefault);
		syncDuration = Integer.valueOf(
				Tools.getSetting(R.string.syncDuration, R.string.syncDurationDefault));
		manualOffset = Integer.valueOf(
				Tools.getSetting(R.string.manualOffset, R.string.manualOffsetDefault));
		manualOGGOffset = Integer.valueOf(
				Tools.getSetting(R.string.manualOGGOffset, R.string.manualOGGOffsetDefault));
		
		// Make the game have higher priority to reduce lag
		//if (priority > 0) Process.setThreadPriority(priority);
		
		// Music setup
		setVolumeControlStream(AudioManager.STREAM_MUSIC); // To control media volume at all times
		
		// Fullscreen?
		if (fullscreen) {
			//requestWindowFeature(Window.FEATURE_NO_TITLE); 
			getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN); 
		}
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		Tools.setScreenDimensions();
		
		// Setup view
		int hardwareAccelerate = Integer.valueOf(
				Tools.getSetting(R.string.hardwareAccelerate, R.string.hardwareAccelerateDefault));
		if (hardwareAccelerate < 0) {
			// Test for hardware acceleration
			View testView = new View(this);
			Canvas testCanvas = new Canvas();
			setContentView(testView);
			// Hardware acceleration support was added in Honeycomb (11)
			if (Build.VERSION.SDK_INT >= 11 && testView.isHardwareAccelerated() && testCanvas.isHardwareAccelerated()) {
				try {
					// Check if clipPath is supported
					testCanvas.clipPath(null, null);
					
					// Looks like it's supported, enable hardware acceleration
					hardwareAccelerate = 1;
				} catch (UnsupportedOperationException e) {
					hardwareAccelerate = 0;
				}
			} else {
				hardwareAccelerate = 0;
			}
		}
		if (hardwareAccelerate == 1) {
			mView = new RefreshHandlerView(this);
		} else {
			// SurfaceView is not hardware accelerated but faster than normal Views
			mView = new SurfaceHolderView(this);
		}
		setContentView(mView.getView());
		
		if (Tools.gameMode == Tools.OSU_MOD) {
			h = new GUIHandlerOsu();
		} else {
			h = new GUIHandlerTap();
		}
		
		// 1.5 and 1.6 have incomplete multitouch support
		// See http://developer.android.com/reference/android/os/Build.VERSION_CODES.html
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ECLAIR) {
			listeners = new GUIListenersNoMulti(h);
		} else {
			listeners = new GUIListenersMulti(h);
		}
		mView.getView().setOnTouchListener(listeners.getOnTouchListener());
		
		this.dp = MenuStartGame.dp;
		h.loadSongData(dp);
		String musicFilePath = dp.df.getMusic().getPath();
		mp = new MusicService(musicFilePath);
		if (Tools.isOGGFile(musicFilePath)) {
			manualOffset += manualOGGOffset;
		}
		
		// Scoreboard		
		h.score.loadHighScore(dp.df.md5hash + dp.getNotesData().getDifficultyMeter());
		
		// Title
		String songtitle = dp.df.getTitle();
		if (songtitle.length() <= 1) {
			songtitle = dp.df.getTitleTranslit();
		}
		title = Tools.getString(R.string.Titlebar) + songtitle + Tools.getString(R.string.GUIGame_by) + dp.df.getArtist();
		this.setTitle(title);
		
		// Vibrate!
		if (additionalVibrations) {
			((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(300); // ready to rumble!
		}
		
		// Setup display
		h.setupXY();
		mView.getGameView().setupDraw();
		if (orientation != ROTATABLE) setRequestedOrientation(orientation);
		
		//Preload commonly used bitmaps
		/*for (int pitch=0; pitch<Tools.PITCHES; pitch++) {
			Bitmap b;
			for (Integer frac: new int[] {4, 8, 12, 16, 24, 32, 48, 64, 192}) {
				b = drawarea.getBitmap(GUINoteImage.rsrc(pitch, frac, false));
			}
			b = drawarea.getBitmap(GUINoteImage.rsrc(pitch, 0, true));
			b = drawarea.getBitmap(GUINoteImage.rsrc(pitch, 0, false));
		}*/

        //set up breath goal -gp
        if (breathGoal == 0) {
            inBreath = 4;
            outBreath = 6;
            span = inBreath + outBreath;
        }

		// Start updating
		mView.startTimer();
		h.setMessageLong(Tools.getString(R.string.GUIGame_ready), 0, 64, 255); // royal blue
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		Tools.setScreenDimensions();
		h.setupXY();
		mView.getGameView().setupBG();
	}
	public static int currentTime = 0;
	
	private class SurfaceHolderThread extends Thread {
		private SurfaceHolder _surfaceHolder;
		private GameView _view;
		private boolean _run = false;
		private boolean _paused = false;
	 
		public SurfaceHolderThread(SurfaceHolder surfaceHolder, GameView view) {
			_surfaceHolder = surfaceHolder;
			_view = view;
		}
		
		public void setPaused(boolean paused) {
			_paused = paused;
		}
		
		public void setRunning(boolean run) {
			_run = run;
		}
	 
		@Override
		public void run() {
			Canvas c;
			while (_run) {
				c = null;
				try {
					synchronized (_surfaceHolder) {
						c = _surfaceHolder.lockCanvas(null);
						if (_view != null && c != null) {
							if (!_paused) {
								_view.update();
							}
							_view.onDraw(c);
						}
					}
				} finally {
					// do this in a finally so that if an exception is thrown
					// during the above, we don't leave the Surface in an
					// inconsistent state
					if (c != null) {
						_surfaceHolder.unlockCanvasAndPost(c);
					}
				}
			}
		}
	}
	
	private class GameView {
		private int frameCount = 0;
		private int frameCountTotal = 0;
		private long fpsStartTime = 0;
		private long fpsTotalTime = 0;
		private double fps = 0;
		private double fpsTotal = 0;
		//private String fpsTruncated = "";
		//private String fpsTruncatedTotal = "";
		private boolean fpsTotalStarted = false;
		
		public void startTimer() {
			// To be safe
			mStartTime = 0;
			musicStartTime = 0;
			travelOffset = 0;
			mFrameNo = 0;
			countDown = -75; // 75 frames before the song begins
			syncCounter = 0;
		}
		
		private int margin, height;
		private Bitmap bgImage;
		private Paint bgImageFiltering, bgSolidPaint;
		private Matrix bgImageMatrix;
		private GUITextPaint textPaint;
		private GUITextPaint textComboPaint;
		private Paint hpBackPaint, hpBarPaint, hpBorderPaint;
		private GUITextPaint leftSettingsPaint, rightSettingsPaint;
		private String leftSettingsTop, leftSettingsBottom;
		private String rightSettingsBottom, rightSettingsTop;
		private GUITextPaint textScorePaint, autoPlayPaint;
		private GUITextPaint titlePaint;
		private Paint titlebarPaint;

		private GUIScoreDisplay scoreDisplay;

        /*------------------------------------------------------------------------------*/
        public int leftShift, topShift;
        public Bitmap bgImageUnscaled;
        public File bg;
        public Canvas bgImageCanvas;
        public int scaled_width, scaled_height;
        public Bitmap bgImageScaled;
        public Paint bgAlpha;
        public Paint rectAlpha;
        /*------------------------------------------------------------------------------*/
		public void clearBitmaps() {
			if (bgImage != null) {
				bgImage.recycle(); //grr garbage collector
				bgImage = null;
			}
		}
		
		private void setupBG() {
			
			// Dimensions
			margin = Tools.scale(7);
			height = Tools.scale(17);
			
			// Background
			clearBitmaps();
			if (backgroundShow) {

				bg = dp.df.getBackground();
				if (bg != null && bg.canRead() && backgroundSong) {
					// Background image file finding is done within DataFile's setBackground
					bgImageUnscaled = BitmapFactory.decodeFile(bg.getPath());
				} else {
					bgImageUnscaled = BitmapFactory.decodeFile(Tools.getBackgroundRes());
				}
				if (bgImageUnscaled != null) {
					// Create a new canvas to draw background image on
					bgImage = Bitmap.createBitmap(Tools.screen_w, Tools.screen_h, Config.RGB_565);
					bgImageCanvas = new Canvas(bgImage);
					
					// Set scaling and alpha
					scaled_width = Tools.screen_w;
                    scaled_height = Tools.screen_h;
					if (Tools.screen_h > Tools.screen_w) {
						scaled_width = Tools.screen_h * bgImageUnscaled.getWidth() / bgImageUnscaled.getHeight();
						leftShift = (Tools.screen_w - scaled_width) / 2;
						topShift = 0;
					} else {
						scaled_height = Tools.screen_w * bgImageUnscaled.getHeight() / bgImageUnscaled.getWidth();
						leftShift = 0;
						topShift = (Tools.screen_h - scaled_height) / 2;
					}
					bgImageScaled = Bitmap.createScaledBitmap(
							bgImageUnscaled, scaled_width, scaled_height, true);
					bgAlpha = new Paint();
					bgAlpha.setAlpha(Tools.MAX_OPA * backgroundBrightness / 100);
					
					// Draw background image onto canvas
					bgImageCanvas.drawBitmap(bgImageScaled, leftShift, topShift, bgAlpha);
					bgImageUnscaled.recycle(); bgImageUnscaled = null; //grr garbage collector
					bgImageScaled.recycle(); bgImageScaled = null; //grr garbage collector
					
					// Draw grey boxes behind info text
					rectAlpha = new Paint(); // default colour is black
					rectAlpha.setAlpha(Tools.MAX_OPA * backgroundBrightness / (4 * 100));
					bgImageCanvas.drawRect(0, 0, Tools.screen_w, (margin * 3 + height * 3), rectAlpha);
					bgImageCanvas.drawRect(0, Tools.screen_h - (margin + height * 2), Tools.screen_w, Tools.screen_h, rectAlpha);
					
					// Draw Tapboxes
					GUIGame.this.h.drawTapboxes(bgImageCanvas); // Draw it directly on the background					
					
					// Set filtering and matrix
					if (backgroundFiltering) {
						bgImageFiltering = new Paint();
						bgImageFiltering.setFilterBitmap(true);
					} else {
						bgImageFiltering = null;
					}
					bgImageMatrix = new Matrix();
					bgImageMatrix.reset();
				}
			}
		}
		
		private void setupDraw() {
			setupBG();
			
			// Background
			bgSolidPaint = new Paint();
			bgSolidPaint.setARGB(Tools.MAX_OPA, 0,0,0);

			// Accuracy message
			textPaint = new GUITextPaint(Tools.scale(32)).alignCenter().serif().
				bold().italic().strokeWidth(Tools.scale(3));
			
			// Combo
			textComboPaint = new GUITextPaint(Tools.scale(36)).alignCenter().sansSerif().
				bold().strokeWidth(Tools.scale(3));
			
			// Health
			hpBackPaint = new Paint();
			hpBackPaint.setARGB(60,0,0,255);
			hpBarPaint = new Paint();
			hpBorderPaint = new Paint();
			hpBorderPaint.setStyle(Paint.Style.STROKE);
			hpBorderPaint.setStrokeWidth(Tools.scale(2));
			hpBorderPaint.setARGB(Tools.MAX_OPA, 0, 0, 0);
			
			// Left Settings (difficulty and credits)
			leftSettingsPaint = new GUITextPaint(Tools.scale(13)).alignLeft().serif().
				italic().ARGB(170, 255, 255, 255);
			
			// Credits
			//leftSettingsTop = dp.df.getCredit();
            leftSettingsTop = "";
			
			// Difficulty
			leftSettingsBottom =
				dp.getNotesData().getDifficulty().toString() + " [" + 
				dp.getNotesData().getDifficultyMeter() + "]";
			
			// Right Settings (modifiers)
			rightSettingsPaint = new GUITextPaint(Tools.scale(13)).alignRight().serif().
				italic().ARGB(170, 255, 255, 255);

			// Modifiers
			rightSettingsBottom = "";
			if (dark) rightSettingsBottom += Tools.getString(R.string.GUIGame_dark);
			switch(noteAppearance) {
				case 1: rightSettingsBottom += Tools.getString(R.string.GUIGame_hidden); break;
				case 2: rightSettingsBottom += Tools.getString(R.string.GUIGame_sudden); break;
				case 3: rightSettingsBottom += Tools.getString(R.string.GUIGame_invisible); break;
				default: break;
			}
			if (jumps && !osu) rightSettingsBottom += Tools.getString(R.string.GUIGame_jumps);
			switch (randomize) {
				case Randomizer.OFF:
					if (!holds && !osu)
						rightSettingsBottom += Tools.getString(R.string.GUIGame_no_holds);
					break;
				case Randomizer.STATIC:
					rightSettingsBottom += Tools.getString(R.string.GUIGame_randomize_static);
					break;
				case Randomizer.DYNAMIC:
					rightSettingsBottom += Tools.getString(R.string.GUIGame_randomize_dynamic);
					break;
			}
			//if (osu) rightSettingsBottom += Tools.getString(R.string.GUIGame_osu_mod);
			if (rightSettingsBottom.length() > 2) {
				rightSettingsBottom = rightSettingsBottom.substring(2); // ignore the first ", "
			}
            //else {
			//	rightSettingsBottom = Tools.getString(R.string.GUIGame_standard);
		//	}

			// Beats Per Minute
			//rightSettingsTop = String.format("%s Beats Per Min", dp.df.getBPMRange(dp.notesDataIndex));
            rightSettingsTop = "";

            // BPM & Speed
           /* rightSettingsTop =
                    String.format(
                            "%s Beats Per Min %3.2fx",
                            dp.df.getBPMRange(dp.notesDataIndex),
                            speed_multiplier
                    ); */

			
			// Title
			titlePaint = new GUITextPaint(Tools.scale(14)).alignLeft().bold().ARGB(Tools.MAX_OPA, 0, 0, 0);
			titlebarPaint = new Paint();
			titlebarPaint.setColor(Color.LTGRAY);
			titlebarPaint.setAlpha(Tools.MAX_OPA * 3 / 4);
			
			// Score
			textScorePaint = new GUITextPaint(Tools.scale(13)).alignRight().monospace().
				italic().ARGB(Tools.MAX_OPA, 255, 255, 255);
			
			// Show FPS or Auto-Play
			if (showFPS || autoPlay) {
				autoPlayPaint = new GUITextPaint(Tools.scale(13)).alignLeft().monospace().
					italic().ARGB(Tools.MAX_OPA, 255, 255, 255);
			} else {
				autoPlayPaint = null;
			}
			
			//Endgame score display
			scoreDisplay = new GUIScoreDisplay(h.score);
			
			// Tracking
			HashMap<String,String> attributes = new HashMap<String,String>();
			attributes.put("leftSettingsTop", leftSettingsTop);
			attributes.put("leftSettingsBottom", leftSettingsBottom);
			attributes.put("rightSettingsTop", rightSettingsTop);
			attributes.put("rightSettingsBottom", rightSettingsBottom);
			ToolsTracker.data("Game started", attributes);
		}

		public void onDraw(Canvas canvas) {

			// FPS
			/*if (showFPS) {
				frameCount++;
				if (frameCount > 10) {
					long fpsCurrentTime = SystemClock.elapsedRealtime(); 
					fps = ((double)(frameCount) / ((double)(fpsCurrentTime - fpsStartTime) / 1000d));
					// Truncate to 2 decimal places
					//fpsTruncated = String.format("%.2f", fps); // Too long, use (int)fps
					if (fpsTotalStarted) {
						frameCountTotal += frameCount;
						fpsTotalTime += fpsCurrentTime - fpsStartTime;
						fpsTotal = ((double)(frameCountTotal) / ((double)(fpsTotalTime) / 1000d));
						//fpsTruncatedTotal = String.format("%.2f", fpsTotal); // Too long, use (int)fpsTotal
					} else {
						if (fps > 0 && fps < 99) { // Sanity
							fpsTotalStarted = true;
						}
					}
					
					frameCount = 0;
					fpsStartTime = SystemClock.elapsedRealtime();
				}
			} */
			
			// Just in case
			if (h == null) {
				return;
			}
									
			// Background, modified to have breathing -gp
			if (bgImage != null && !showBreath) {
				canvas.drawBitmap(bgImage, bgImageMatrix, bgImageFiltering);
			} else if (showBreath) {
                canvas.drawPaint(bgSolidPaint);
                //h.drawTapboxes(canvas); // this is too slow -gp
            }
            else {
                canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), bgSolidPaint);
            }

			// Arrows
			//GUIFallingObject[] falling_objs = h.get_objs();
			if (h.fallingobjects == null)
				return;
			h.drawFallingObjects(canvas, drawarea);
			
			// Title
			canvas.drawRect(0, 0, Tools.screen_w, margin + height, titlebarPaint);
			titlePaint.draw(canvas, title, margin, height);

            // Health
            double health = h.score.getHealthPercent();
            respRate = MenuHome.bpm;
            // Turns the Health Bar blue when the player is under their target rate  to show them they get extra points! Star Power!
            if (respRate!= null) {
                double resp = Double.parseDouble(respRate);

                MenuHome.sampleDB.execSQL("INSERT INTO " +
                        MenuHome.SAMPLE_TABLE_NAME +
                        " Values ('test','test');");

                // Modify Speed Based on Breathing Rate
                switch (Integer.parseInt(Tools.getSetting(R.string.goalLevel, R.string.goalLevelDefault))) {
                    // GOAL: 6
                    case 0:
                        if (resp <= 6.0) { hpBarPaint.setARGB(Tools.MAX_OPA, 7, 191, 247); }
                        else {
                            if (health > 0.5)
                                hpBarPaint.setARGB(Tools.MAX_OPA, (int) ((1 - health) * 255 * 2), 255, 0);
                            else if (health > 0)
                                hpBarPaint.setARGB(Tools.MAX_OPA, 255, (int) (health * 255 * 2), 0);
                                //hpBarPaint.setARGB(Tools.MAX_OPA, 7, 191, 247);
                            else {
                                hpBarPaint.setARGB(Tools.MAX_OPA, 127, 0, 0);
                                health = 1; //for drawing purposes only
                            }
                        }
                        break;

                    // GOAL: 8
                    case 1:
                        if (resp <= 8.0) { hpBarPaint.setARGB(Tools.MAX_OPA, 7, 191, 247); }
                        else {
                            if (health > 0.5)
                                hpBarPaint.setARGB(Tools.MAX_OPA, (int) ((1 - health) * 255 * 2), 255, 0);
                            else if (health > 0)
                                hpBarPaint.setARGB(Tools.MAX_OPA, 255, (int) (health * 255 * 2), 0);
                                //hpBarPaint.setARGB(Tools.MAX_OPA, 7, 191, 247);
                            else {
                                hpBarPaint.setARGB(Tools.MAX_OPA, 127, 0, 0);
                                health = 1; //for drawing purposes only
                            }
                        }
                        break;

                    // GOAL: 10
                    case 2:
                        if (resp <= 10.0) { hpBarPaint.setARGB(Tools.MAX_OPA, 7, 191, 247); }
                        else {
                            if (health > 0.5)
                                hpBarPaint.setARGB(Tools.MAX_OPA, (int) ((1 - health) * 255 * 2), 255, 0);
                            else if (health > 0)
                                hpBarPaint.setARGB(Tools.MAX_OPA, 255, (int) (health * 255 * 2), 0);
                                //hpBarPaint.setARGB(Tools.MAX_OPA, 7, 191, 247);
                            else {
                                hpBarPaint.setARGB(Tools.MAX_OPA, 127, 0, 0);
                                health = 1; //for drawing purposes only
                            }
                        }
                        break;

                    // GOAL: 12
                    case 3:
                        if (resp <= 12.0) { hpBarPaint.setARGB(Tools.MAX_OPA, 7, 191, 247); }
                        else {
                            if (health > 0.5)
                                hpBarPaint.setARGB(Tools.MAX_OPA, (int) ((1 - health) * 255 * 2), 255, 0);
                            else if (health > 0)
                                hpBarPaint.setARGB(Tools.MAX_OPA, 255, (int) (health * 255 * 2), 0);
                                //hpBarPaint.setARGB(Tools.MAX_OPA, 7, 191, 247);
                            else {
                                hpBarPaint.setARGB(Tools.MAX_OPA, 127, 0, 0);
                                health = 1; //for drawing purposes only
                            }
                        }
                        break;

                    // GOAL: 14
                    case 4:
                        if (resp <= 14.0) { hpBarPaint.setARGB(Tools.MAX_OPA, 7, 191, 247);}
                        else {
                            if (health > 0.5)
                                hpBarPaint.setARGB(Tools.MAX_OPA, (int) ((1 - health) * 255 * 2), 255, 0);
                            else if (health > 0)
                                hpBarPaint.setARGB(Tools.MAX_OPA, 255, (int) (health * 255 * 2), 0);
                                //hpBarPaint.setARGB(Tools.MAX_OPA, 7, 191, 247);
                            else {
                                hpBarPaint.setARGB(Tools.MAX_OPA, 127, 0, 0);
                                health = 1; //for drawing purposes only
                            }
                        }
                        break;
                }
            }
            // Default Case
            else {
                respRate = "Not Connected";
                if (health > 0.5)
                    hpBarPaint.setARGB(Tools.MAX_OPA, (int) ((1 - health) * 255 * 2), 255, 0);
                else if (health > 0)
                    hpBarPaint.setARGB(Tools.MAX_OPA, 255, (int) (health * 255 * 2), 0);
                    //hpBarPaint.setARGB(Tools.MAX_OPA, 7, 191, 247);
                else {
                    hpBarPaint.setARGB(Tools.MAX_OPA, 127, 0, 0);
                    health = 1; //for drawing purposes only
                }
            }

            canvas.drawRect(new Rect(margin, margin * 2 + height, Tools.screen_w - margin, margin * 2 + height * 2), hpBackPaint);
			canvas.drawRect(new Rect(margin, margin * 2 + height, (int)((Tools.screen_w - margin) * health), margin * 2 + height * 2), hpBarPaint);
			canvas.drawRect(new Rect(margin, margin * 2 + height, Tools.screen_w - margin, margin * 2 + height * 2), hpBorderPaint);
			
			// Show Breaths-Per-Minute on game screen
			if (showFPS) {
				autoPlayPaint.draw(canvas, "BPM:" + respRate, margin * 2, margin * 2 + height * 3);
			}
            //else if (autoPlay) {
			//	autoPlayPaint.draw(canvas, "AUTO", margin * 2, margin + height * 3);
			//}
			
			// Accuracy message
			int opa = (2 * Tools.MAX_OPA - h.msg_frames * frame_millis);
			opa = (opa < 0) ? 0 : (opa > Tools.MAX_OPA) ? Tools.MAX_OPA : opa;
			if (opa > 0) {
				textPaint.ARGB(opa,h.msg_r,h.msg_g,h.msg_b);
				textPaint.strokeARGB(opa,255,255,255);
				textPaint.draw(canvas, h.msg,
						Tools.screen_w/2, Tools.screen_h - Tools.screen_h/3 - h.msg_frames * frame_millis / 10);
			}
			
			// Combo
			int opac = (3 * Tools.MAX_OPA - h.combo_frames * frame_millis);
			opac = (opac < 0) ? 0 : (opac > Tools.MAX_OPA) ? Tools.MAX_OPA : opac;
			if (opac > 0 && h.score.comboCount > GUIHandler.MIN_COMBO) {
				if (h.score.comboCount < 100) {
					textComboPaint.ARGB(opac, 255, 220, 0); // yellow
					textComboPaint.strokeARGB(opac, 255, 192, 0);
				} else if (h.score.comboCount < 500) {
					textComboPaint.ARGB(opac, 255, 160, 0); // light orange
					textComboPaint.strokeARGB(opac, 255, 120, 0);
				} else if (h.score.comboCount < 1000) {
					textComboPaint.ARGB(opac, 255, 128, 0); // orange
					textComboPaint.strokeARGB(opac, 255, 64, 0);
				} else {
					textComboPaint.ARGB(opac, 255, 64, 0); // almost red
					textComboPaint.strokeARGB(opac, 192, 0, 0);
				}
				textComboPaint.draw(canvas, h.combo, Tools.screen_w/2, Tools.screen_h/2);
			}
			
			// Settings
			leftSettingsPaint.draw(canvas, leftSettingsTop, margin, Tools.screen_h - margin - height);
			leftSettingsPaint.draw(canvas, leftSettingsBottom, margin, Tools.screen_h - margin);
			rightSettingsPaint.draw(canvas, rightSettingsTop, Tools.screen_w - margin, Tools.screen_h - margin - height);
			rightSettingsPaint.draw(canvas, rightSettingsBottom, Tools.screen_w - margin, Tools.screen_h - margin);
			
			// Score
			if (h.scoreboard_frames < 0) {
				textScorePaint.draw(canvas, h.score.highScore + "/" + h.score.score + "PTS", Tools.screen_w - margin * 2, margin * 2 + height * 3);
			} else {
				scoreDisplay.updateStatus(h.score.comboCount == h.score.noteCount);
				scoreDisplay.draw(canvas, h.scoreboard_frames * frame_millis);
			}
			
		}
		
		// Timing, public for outside access
		long pauseTime = 0;
		long mStartTime = 0;
		int musicCurrentPosition = 0;
		int musicStartTime = 0;
		int syncAdjust = 0;
		int travelOffset = 0;
        //double tempFall = 0;
		int mFrameNo = 0;
		int countDown = -180; // 180 frames before the song begins, ~3s
		int syncCounter = 0;

        //****************
        //interval used for dynamic speed debugging/testing
        protected static final long TIME_DELAY = 0;

        //Java Timer class object declaration
        Timer timer = new Timer();

        //self-made function to set fallpix_per_ms speed variable within Update()
        class SpeedTask extends TimerTask {
            public void run() {

                respRate = MenuHome.bpm;
                // Check that the device is connected
                if (respRate != null) {
                    double resp = Double.parseDouble(respRate);
                    // Modify Speed Based on Breathing Rate
                    switch (Integer.parseInt(Tools.getSetting(R.string.goalLevel, R.string.goalLevelDefault))) {
                        // GOAL: 6
                        case 0:
                            if (resp <= 6.0) { h.fallpix_per_ms = 1.5; }
                            else if (resp > 6.0 && resp <= 8.0) { h.fallpix_per_ms = 2; }
                            else if (resp > 8.0 && resp <= 10.0) { h.fallpix_per_ms = 2.5; }
                            else if (resp > 10.0 && resp <= 12.0) { h.fallpix_per_ms = 3; }
                            else if (resp > 12.0 && resp <= 14.0) { h.fallpix_per_ms = 3.5; }
                            else if (resp > 14.0 && resp <= 16.0) { h.fallpix_per_ms = 4; }
                            else { h.fallpix_per_ms = 4.5; }
                            break;

                        // GOAL: 8
                        case 1:
                            if (resp <= 8.0) { h.fallpix_per_ms = 1.5; }
                            else if (resp > 8.0 && resp <= 10.0) { h.fallpix_per_ms = 2; }
                            else if (resp > 10.0 && resp <= 12.0) { h.fallpix_per_ms = 2.5; }
                            else if (resp > 12.0 && resp <= 14.0) { h.fallpix_per_ms = 3; }
                            else if (resp > 14.0 && resp <= 16.0) { h.fallpix_per_ms = 3.5; }
                            else if (resp > 16.0 && resp <= 18.0) { h.fallpix_per_ms = 4; }
                            else { h.fallpix_per_ms = 4.5; }
                            break;

                        // GOAL: 10
                        case 2:
                            if (resp <= 10.0) { h.fallpix_per_ms = 1.5; }
                            else if (resp > 10.0 && resp <= 12.0) { h.fallpix_per_ms = 2; }
                            else if (resp > 12.0 && resp <= 14.0) { h.fallpix_per_ms = 2.5; }
                            else if (resp > 14.0 && resp <= 16.0) { h.fallpix_per_ms = 3; }
                            else if (resp > 16.0 && resp <= 18.0) { h.fallpix_per_ms = 3.5; }
                            else if (resp > 18.0 && resp <= 20.0) { h.fallpix_per_ms = 4; }
                            else { h.fallpix_per_ms = 4.5; }
                            break;

                        // GOAL: 12
                        case 3:
                            if (resp <= 12.0) { h.fallpix_per_ms = 1.5; }
                            else if (resp > 12.0 && resp <= 14.0) { h.fallpix_per_ms = 2; }
                            else if (resp > 14.0 && resp <= 16.0) { h.fallpix_per_ms = 2.5; }
                            else if (resp > 16.0 && resp <= 18.0) { h.fallpix_per_ms = 3; }
                            else if (resp > 18.0 && resp <= 20.0) { h.fallpix_per_ms = 3.5; }
                            else if (resp > 20.0 && resp <= 22.0) { h.fallpix_per_ms = 4; }
                            else { h.fallpix_per_ms = 4.5; }
                            break;

                        // GOAL: 14
                        case 4:
                            if (resp <= 14.0) {h.fallpix_per_ms = 1.5;}
                            else if (resp > 14.0 && resp <= 16.0) { h.fallpix_per_ms = 2; }
                            else if (resp > 16.0 && resp <= 18.0) { h.fallpix_per_ms = 2.5; }
                            else if (resp > 18.0 && resp <= 20.0) { h.fallpix_per_ms = 3; }
                            else if (resp > 20.0 && resp <= 22.0) { h.fallpix_per_ms = 3.5; }
                            else if (resp > 22.0 && resp <= 24.0) { h.fallpix_per_ms = 4; }
                            else { h.fallpix_per_ms = 4.5; }
                            break;
                    }
                }

                // if not connected to device
                else {
                    respRate = "Not Connected";
                    h.fallpix_per_ms = 2;
                }
            }
        }

        //Java Timer class object declaration
        Timer timer2 = new Timer();

        //self-made function to show background breathing within Update() -gp
        class SpeedTask2 extends TimerTask {
            public void run() {

                int sections = 1000/interval; // 1000/1000 = 1 section per sec
                int totalCap = span*sections; // 10 sec * 1 section = 10 total sections
                int inCap = (inBreath*sections); // 6 sec * 1 section = 6 in sections
                int outCap = (outBreath*sections); // 4 sec * 1 section = 4 out sections
                int outMid = outCap/2 + inCap; // (4 sec * 1 section)/2 + 6 = 8 sec is mid for outBreath
                int colorIncrement;
                int inColorRange = 200;
                int outColorRange = 255;
                //start the process over
                if (increment >= totalCap) {
                    increment = 0;
                    breathBase = 0;
                }

                //determine increment amount
                if (increment <= inCap) {
                    //we are in inBreath section
                    colorIncrement = (int) Math.floor((double)inColorRange / inCap); // 100/6 = 16

                    //error checking
                    if (colorIncrement < 1)
                        colorIncrement = 1;

                    breathBase+=colorIncrement;

                    //limit to how bright the color can get (if you change this then change additional colors accordingly, ie sum must be <= 255)
                    if (breathBase > 200)
                        breathBase = 200;

                    //Log.d("gusgus", "incremented by " + colorIncrement + " with inCap at " + inCap);
                    if (breathColor == 0) {
                        bgSolidPaint.setARGB(Tools.MAX_OPA, breathBase, breathBase, breathBase);
                    }
                    else if (breathColor == 1) {
                        bgSolidPaint.setARGB(Tools.MAX_OPA, breathBase, 0, 0);
                    }
                    else if (breathColor == 2) {
                        bgSolidPaint.setARGB(Tools.MAX_OPA, 0, breathBase, 0);
                    }
                    else if (breathColor == 3) {
                        bgSolidPaint.setARGB(Tools.MAX_OPA, 0, 0, breathBase);
                    }
                    else if (breathColor == 4) {
                        bgSolidPaint.setARGB(Tools.MAX_OPA, breathBase, breathBase, 0);
                    }
                    else if (breathColor == 5) {
                        bgSolidPaint.setARGB(Tools.MAX_OPA, 0, breathBase, breathBase);
                    }
                    else if (breathColor == 6) {
                        bgSolidPaint.setARGB(Tools.MAX_OPA, breathBase, 0, breathBase);
                    }

                }
                else {
                    //we are in outBreath section
                    colorIncrement = (int) Math.floor((double)outColorRange / outCap); // 100/4 = 25

                    //error checking
                    if (colorIncrement < 1)
                        colorIncrement = 1;

                    int midOfInCapOutMid = ((outMid + inCap)/2);
                    int midOfInCapOutMidAndInCap = (midOfInCapOutMid + inCap)/2;
                    if (flag == 1 || increment <  midOfInCapOutMidAndInCap) {
                        breathBase-=colorIncrement;
                        flag = 0;
                    }
                    else {
                        flag = 1;
                    }



                    //error checking
                    if (breathBase < 0)
                        breathBase = 0;

                    //Log.d("gusgus", "decremented by " + colorIncrement + " with outCap at " + outCap);
                    if (breathColor == 0) {
                        bgSolidPaint.setARGB(Tools.MAX_OPA, breathBase, breathBase, breathBase);
                    }
                    else if (breathColor == 1) {
                        bgSolidPaint.setARGB(Tools.MAX_OPA, breathBase, 0, 0);
                    }
                    else if (breathColor == 2) {
                        bgSolidPaint.setARGB(Tools.MAX_OPA, 0, breathBase, 0);
                    }
                    else if (breathColor == 3) {
                        bgSolidPaint.setARGB(Tools.MAX_OPA, 0, 0, breathBase);
                    }
                    else if (breathColor == 4) {
                        bgSolidPaint.setARGB(Tools.MAX_OPA, breathBase, breathBase, 0);
                    }
                    else if (breathColor == 5) {
                        bgSolidPaint.setARGB(Tools.MAX_OPA, 0, breathBase, breathBase);
                    }
                    else if (breathColor == 6) {
                        bgSolidPaint.setARGB(Tools.MAX_OPA, breathBase, 0, breathBase);
                    }

                }

                //Log.d("gusgus", "opa is " + breathBase);
                increment++;
            }
        }
        //****************

		private void nextFrame() {
			if (h != null) {
				try {
					h.nextFrame();
				} catch (Exception e) {
					if (Tools.getBooleanSetting(R.string.debugLogCat, R.string.debugLogCatDefault)) {
					Tools.toast_long(
							Tools.getString(R.string.GUIGame_nextFrame_error) + 
							Tools.getString(R.string.Tools_error_msg) +
							e.getMessage() +
							Tools.getString(R.string.Tools_notify_msg)
							);
					}
					ToolsTracker.error("GUIGame.nextFrame", e, title);
					Log.e("GUIGame.nextFrame", Log.getStackTraceString(e));
				}
			}
		}

		public void update() {
			// Initial "Ready" countdown"
			if (countDown < 0) {
				currentTime = countDown * frame_millis + manualOffset;
				nextFrame();
				countDown++;
			// Countdown done, start the music!
			} else if (countDown == 0) {
				mp.startPlaying();
				travelOffset = h.travel_offset_ms();

                // Modifies speed value, called using TimerTask class
                timer.schedule(new SpeedTask(), TIME_DELAY, 500);
                // If user selects to show breathing background -gp
                if (showBreath)
                    timer2.schedule(new SpeedTask2(), TIME_DELAY, interval);

                musicCurrentPosition = mp.getCurrentPosition();
				musicStartTime = musicCurrentPosition + manualOffset;
				mStartTime = SystemClock.elapsedRealtime();
				countDown++; // Countdown positive, no more countdown!
				currentTime = (int)(SystemClock.elapsedRealtime() - mStartTime + musicStartTime);
			// Game is running
			} else {				
				currentTime = (int)(SystemClock.elapsedRealtime() - mStartTime + musicStartTime);
				int desiredFrameNo = (int)((currentTime + travelOffset)/frame_millis);
				
				// Sync during the first x ms
				// Some re-syncing issue with OGGs due to seekTo, so syncAdjust slowly
				if (syncCounter * frame_millis < syncDuration) {
					if (mp.isPlaying()) {
						if (syncCounter % 2 == 0) {
							musicCurrentPosition = mp.getCurrentPosition();
							syncAdjust = (currentTime - (musicCurrentPosition + manualOffset)) / 8;
							mStartTime += syncAdjust;
							if (debugTime) {
								h.setMessage(
										"m" + musicCurrentPosition + 
										"/t" + currentTime + 
										"/d" + syncAdjust
										, 0, 0, 0);
							}
						}
					}
					syncCounter++;
				}
				
				while (mFrameNo < desiredFrameNo) {
					nextFrame();
					mFrameNo++;
				}
			}
		}
	}
	
	private interface GameViewHandler {
		View getView();
		GameView getGameView();
		void startTimer();
		void startUpdating();
		void stopUpdating();
		void forceUpdate();
		void update();
	}
	
	private class RefreshHandlerView extends View implements GameViewHandler {
		private GameView mView;
		private RefreshHandler mRedrawHandler;
		
		public RefreshHandlerView(Context context) {
			super(context);
			mView = new GameView();
			mRedrawHandler = new RefreshHandler();
			this.setFocusable(true);
			this.requestFocus();
			this.startTimer();
		}
		
		public View getView() {
			return this;
		}
	
		public GameView getGameView() {
			return mView;
		}
		
		public void update() {
			mView.update();
			mRedrawHandler.start();
		}
		
		public void startTimer() {
			mView.startTimer();
			mRedrawHandler.start();
		}
		
		public void startUpdating() {
			mRedrawHandler.start();
		}
		
		public void stopUpdating() {
			mRedrawHandler.removeCallbacksAndMessages(null);
		}
		
		public void forceUpdate() {
			mRedrawHandler.handleMessage(null);
		}
		
		@Override
		public void onDraw(Canvas canvas) {
			mView.onDraw(canvas);
		}
		
		class RefreshHandler extends Handler {
			@Override
			public void handleMessage(Message msg) {
				RefreshHandlerView.this.update();
				RefreshHandlerView.this.invalidate();
			}
			
			public void start() {
				this.removeMessages(0);
				sendMessage(obtainMessage(0));
			}
		}
	}
	
	private class SurfaceHolderView extends SurfaceView implements SurfaceHolder.Callback, GameViewHandler {
		public SurfaceHolderThread _thread;
		private GameView mView;
		
		public SurfaceHolderView(Context context) {
			super(context);
			mView = new GameView();
			getHolder().addCallback(this);
			_thread = new SurfaceHolderThread(this.getHolder(), mView);
		}
		
		public View getView() {
			return this;
		}
		
		public GameView getGameView() {
			return mView;
		}
		
		public void update() {
			mView.update();
		};
		
		public void startTimer() {
			mView.startTimer();
			mView.update();
		}
		
		public void startUpdating() {
			_thread.setPaused(false);
		}
		
		public void stopUpdating() {
			_thread.setPaused(true);
		}
		
		public void forceUpdate() {
			// TODO Auto-generated method stub
		}
	
		public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
			// TODO Auto-generated method stub
		}
		
		public void surfaceCreated(SurfaceHolder holder) {
			_thread = new SurfaceHolderThread(holder, mView);
			_thread.setRunning(true);
			if (!_thread.isAlive()) {
				_thread.start();
			}
			// Hack sync fix
			//resumeGame(false);
			pauseGame(false, false);
			resumeGame(false);
		}

		public void surfaceDestroyed(SurfaceHolder holder) {
			boolean retry = true;
			_thread.setRunning(false);
			while (retry) {
				try {
					_thread.join();
					retry = false;
				} catch (InterruptedException e) {
					// we will try it again and again...
				}
			}
		}
	}
	
	private void stopGame(String msg, int r, int g, int b, boolean screenshot, boolean showText) {
		mp.pausePlaying();
		if (mView != null && !h.score.isPaused) {
			if (!screenshotMode) {
				if (showText) {
					h.setMessage(msg, r, g, b);
				}
				mView.forceUpdate();
			}
			mView.stopUpdating();
			mView.getGameView().pauseTime = SystemClock.elapsedRealtime();
			h.score.isPaused = true;
			if (screenshotMode && screenshot) {
				//Tools.takeScreenshot(mView);
				//Tools.takeScreenshot(this.getWindow().getDecorView(), dp.df.getTitle()); // This includes both the header AND mView - I don't know why >_>
				takeScreenshot();
			}
		}
		h.pauseVibrator();
	}
	
	private void takeScreenshot() {
		Bitmap screenshot = Bitmap.createBitmap(Tools.screen_w, Tools.screen_h, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(screenshot);
		mView.getGameView().onDraw(canvas);
		Tools.takeScreenshot(screenshot, dp.df.getTitle());
	}
	
	private void pauseGame(boolean screenshot) {
		pauseGame(screenshot, true);
	}
	private void pauseGame(boolean screenshot, boolean showText) {
		stopGame(Tools.getString(R.string.GUIGame_paused), 255, 190, 0, screenshot, showText); // gold
	}
	private void exitGame() {
		stopGame(Tools.getString(R.string.GUIGame_exiting), 0, 64, 255, false, true); // royal blue
	}
	private void resumeGame() {
		resumeGame(true);
	}
	private void resumeGame(boolean showText) {
		if (mView != null && h.score.isPaused) {
			if (showText) h.setMessage(Tools.getString(R.string.GUIGame_resumed), 255, 190, 0); // gold
			if (!(h.done || h.score.gameOver)) {
				mp.resumePlaying();
				mView.getGameView().syncCounter = -200; // Sync for at least 200 frames
			}
			long diff = (SystemClock.elapsedRealtime() - mView.getGameView().pauseTime);
			mView.getGameView().mStartTime += diff;
			//mView.fpsStartTime += diff;
			mView.startUpdating();
			h.score.isPaused = false;
		}
	}
	
	@Override
	public void onWindowFocusChanged (boolean hasFocus) {
		if (hasFocus) {
			Tools.setContext(this);
			this.setTitle(title);
			if (h.score.isPaused) { 
				resumeGame(mView.getGameView().countDown >= 0);
			}
		} else if (!hasFocus && !autoPlay) { // continue even if screen turns off
			pauseGame(false);
		}
	}
	
	private void clearBitmaps() {
		/*
		 * Delete any bitmaps still lying around in memory.
		 * This really shouldn't be necessary...
		 * 
		 * Not sure where the best place to call this is.
		 * onDestroy isn't necessarily called at a useful time, but onPause
		 * could potentially be resumed and then we have no images. This is fine
		 * for the arrow image cache (they'll just get autoreloaded), not fine for the
		 * background (needs to be reloaded manually).
		 * 
		 * http://stackoverflow.com/questions/1949066/java-lang-outofmemoryerror-bitmap-size-exceeds-vm-budget-android
		 * http://code.google.com/p/android/issues/detail?id=8488
		 * */
		mView.getGameView().clearBitmaps();
		drawarea.clearBitmaps();
		System.gc();
	}
	
	@Override
	protected void onDestroy() {
		//Log.d("GUIGame", "GUIGame destroyed.");
		clearBitmaps();
		exitGame();
		mp.onDestroy();
		h.releaseVibrator();
		super.onDestroy();
	}
	
	@Override
	//TODO somehow move these to GUIListeners. This may require passing this GUIGame to the listener as a a param.
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if ((keyCode == KeyEvent.KEYCODE_BACK)) {
			exitGame(); // this is needed cause something things go screwy when autoPlay is on
		}
		int pitch = GUIListeners.keyCode2Direction(keyCode);

		if (keyCode == KeyEvent.KEYCODE_SEARCH || keyCode == KeyEvent.KEYCODE_MENU) {
			if (h.score.isPaused) 
				resumeGame();
			else 
				pauseGame(true);
			return true;
		}
		
		if (autoPlay || h.done || h.score.gameOver || pitch == -1)
			return super.onKeyDown(keyCode, event);	
		else
			return h.onTouch_Down_One(pitch);
	}
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		int pitch = GUIListeners.keyCode2Direction(keyCode);

		if (autoPlay || h.done || h.score.gameOver)
			return super.onKeyUp(keyCode, event);
		
		if (keyCode == KeyEvent.KEYCODE_SEARCH)
			return true; // Search function is annoying ><
		
		if (pitch == -1)	
			return super.onKeyUp(keyCode, event);
		else
			return h.onTouch_Up(1 << pitch);
	}
}