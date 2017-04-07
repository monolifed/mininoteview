package com.mininoteview.mod;


import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Layout;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Toast;

import java.io.File;
import java.security.MessageDigest;
import java.util.Date;


public class TextEdit extends Activity
{
	//改行コード
	public static final int LINEBREAK_AUTO = 0;
	public static final int LINEBREAK_CRLF = 1;
	public static final int LINEBREAK_LF = 2;
	public static final int LINEBREAK_CR = 3;
	// ------------------------------
	// 定数定義
	// ------------------------------
	private static final int MENUID_CLOSE = Menu.FIRST + 1;
	private static final int MENUID_SAVE = Menu.FIRST + 2;
	private static final int MENUID_SAVE_AS = Menu.FIRST + 3;

	private static final int SHOW_FILELIST_OPEN = 0;
	private static final int SHOW_FILELIST_SAVE = SHOW_FILELIST_OPEN + 1;
	final Handler notifyHandler = new Handler();

	private String filepath = Environment.getExternalStorageDirectory().getAbsolutePath();
	private File aFile;
	private boolean isEncrypt = false;
	//for title bar
	private String mAppName = "";
	private int selStart = 0;
	private int selStop = 0;
	private String strText = "";
	private byte[] binText = null;
	private EditText edit;
	private String ErrorMessage = "";
	private ProgressDialog progressDlg;
	private boolean ignoreOnResume = false;
	private int mResetTimer = 3;
	private long mOnPauseTime = 0;
	// Preferences
	private String charsetName = "UTF-8";
	private int lineBreakSetting = LINEBREAK_AUTO;
	private Typeface mTypeface = Typeface.DEFAULT;
	private boolean showBottomBarFlag = true;
	private float fontSize = 18;
	private boolean autoSaveFlag = false;
	private boolean onPauseBeforeSelectSaveFile = false;
	private boolean closeAfterSaveFlag = false;

	final Runnable run_file_acc_err = new Runnable()
	{
		public void run()
		{
			progressDlg.dismiss();
			showMessage(ErrorMessage);
			ErrorMessage = "";
			closeAfterSaveFlag = false;
		}
	};

	private String messageDigest;

	final Runnable run_readFinished = new Runnable()
	{
		public void run()
		{
			// ファイル処理終了後の処理を記述
			progressDlg.dismiss();

			//header check
			//String BFHeader = "";
			//if(binText.length >= 4) BFHeader = new String(binText, 0, 4);
			if(MyUtil.isChiData(binText) && !filepath.endsWith(".txt"))
			{//This File is BF01
				if(PasswordBox.getPassDigest() == null)
				{
					getPasswordAndDecryptData();
				}
				else
				{
					decryptData();
					setStringToEditText();
					setSelection();
				}

			}
			else
			{
				try
				{
					setBinTextToStrText();
				}
				catch(Exception e)
				{
					e.printStackTrace();
					showMessage(e.toString());
				}
				setStringToEditText();
				setSelection();
			}
			messageDigest = getMessageDigest();
		}
	};

	final Runnable run_writeFinished = new Runnable()
	{
		public void run()
		{
			String s = aFile.getName();
			setTitleBarText(aFile.getName());


			progressDlg.dismiss();
			int duration = Toast.LENGTH_LONG;
			if(autoSaveFlag) duration = Toast.LENGTH_SHORT;
			Toast.makeText(TextEdit.this, getString(R.string.notify_file_save) + ": " + s, duration).show();


			setResultForActionGetContent();

			if(closeAfterSaveFlag)
			{
				finish();
			}

			//message digest 取得 ファイルを正常に保存できたのでdigest更新
			messageDigest = getMessageDigest();


		}
	};
	//Scroll中かどうかを示すフラグ
	private boolean onScrollFlag = false;
	//private boolean hideIMEFlag = false;
	// キーイベント発生時、呼び出されます
	private boolean mBackKeyDown = false;

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);
		mAppName = getString(R.string.app_name);

		initConfig();
		Intent intent = getIntent();

		if(Intent.ACTION_VIEW.equals(getIntent().getAction()) ||
				Intent.ACTION_EDIT.equals(getIntent().getAction()) ||
				Intent.ACTION_GET_CONTENT.equals(getIntent().getAction())
				)
		{
			Uri uri = intent.getData();
			if(uri != null)
			{
				filepath = uri.getPath();
			}
		}
		else
		{
			filepath = intent.getStringExtra("FILEPATH");
			selStart = intent.getIntExtra("SELSTART", 0);
			selStop = intent.getIntExtra("SELSTOP", 0);
		}
		aFile = new File(filepath);

		setContentView(R.layout.editbox);
		edit = (EditText) findViewById(R.id.editbox);
		edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);

		edit.setTypeface(mTypeface);

		registerForContextMenu(edit);


		if(aFile.isDirectory())
		{
			setTitleBarText("(" + getString(R.string.alert_dialog_newfile) + ")");
			messageDigest = getMessageDigest();

		}
		else
		{
			this.getWindow().setSoftInputMode(LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

//			TextEdit.this.setTitle(getString(R.string.app_name) + " - " + aFile.getName());
			setTitleBarText(aFile.getName());
			progressDlg = ProgressDialog.show(this, null, "Now Loading...", true, false);
			new FileReadThread().start();
		}


		if(showBottomBarFlag)
		{
			addBottomBar();
		}

		//initListener();
		initListener1();
	}

	private void initConfig()
	{
		//PasswordBoxのタイマーセット
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		String timerString = sharedPreferences.getString(getString(R.string.prefPWResetTimerKey), "3");
		mResetTimer = Integer.parseInt(timerString);
		PasswordBox.setTimerVal(mResetTimer);

		//charset name セット
		charsetName = sharedPreferences.getString(getString(R.string.prefCharsetNameKey), "utf-8");

		//showBottomBarFlagセット
		showBottomBarFlag = sharedPreferences.getBoolean(getString(R.string.prefShowButtonsKey), true);

		//fontsizeセット
		fontSize = sharedPreferences.getFloat(getString(R.string.prefFontSizeKey), fontSize);

		//linebreakセット
		String linebreakString = sharedPreferences.getString(getString(R.string.prefLineBreakCodeKey), "auto");
		if(linebreakString.equalsIgnoreCase("auto"))
		{
			lineBreakSetting = LINEBREAK_AUTO;
		}
		else if(linebreakString.equalsIgnoreCase("crlf"))
		{
			lineBreakSetting = LINEBREAK_CRLF;
		}
		else if(linebreakString.equalsIgnoreCase("lf"))
		{
			lineBreakSetting = LINEBREAK_LF;
		}
		else if(linebreakString.equalsIgnoreCase("cr"))
		{
			lineBreakSetting = LINEBREAK_CR;
		}

		//intentでファイルパスを渡されなかったときの為にinitDirをfilepathにセット
		filepath = Environment.getExternalStorageDirectory().getAbsolutePath();

		//autoSaveFlagセット
		autoSaveFlag = sharedPreferences.getBoolean(getString(R.string.prefAutoSaveKey), false);

		//typefaceセット
		String typefaceString = sharedPreferences.getString(getString(R.string.prefTypefaceKey), "DEFAULT");
		if(typefaceString.equalsIgnoreCase("DEFAULT"))
		{
			mTypeface = Typeface.DEFAULT;
		}
		else if(typefaceString.equalsIgnoreCase("MONOSPACE"))
		{
			mTypeface = Typeface.MONOSPACE;
		}
		else if(typefaceString.equalsIgnoreCase("SANS_SERIF"))
		{
			mTypeface = Typeface.SANS_SERIF;
		}
		else if(typefaceString.equalsIgnoreCase("SERIF"))
		{
			mTypeface = Typeface.SERIF;
		}

		boolean noTitleBarFlag = sharedPreferences.getBoolean(getString(R.string.prefNoTitleBarKey), false);
		if(noTitleBarFlag) requestWindowFeature(Window.FEATURE_NO_TITLE);


	}

	private void addBottomBar()
	{
		View bottombar = getLayoutInflater().inflate(R.layout.bottom_bar, null);
		ViewGroup editboxlayout = (ViewGroup) findViewById(R.id.editboxlayout);

		// ボタンのクリックリスナー
		Button btnUpDir = (Button) bottombar.findViewById(R.id.LeftButton);
		btnUpDir.setText(R.string.BottomMenu_close);
		btnUpDir.setOnClickListener(new View.OnClickListener()
		{

			//		@Override
			public void onClick(View v)
			{
				//ボタンを押したときの動作
				closeFile();
			}

		});
		// ボタンのクリックリスナー
		Button btnMenu = (Button) bottombar.findViewById(R.id.RightButton);
		btnMenu.setText(R.string.BottomMenu_menu);
		btnMenu.setOnClickListener(new View.OnClickListener()
		{

			//		@Override
			public void onClick(View v)
			{
				//ボタンを押したときの動作
				openOptionsMenu();
			}

		});
		editboxlayout.addView(bottombar);

	}

	private void moveCursor(int x, int y)
	{
		x -= edit.getPaddingLeft();
		y -= edit.getPaddingTop();
		Layout l = edit.getLayout();
		int offset;
		int line = l.getLineForVertical(y);
		if(line == 0 && y < l.getLineTop(line))
		{
			offset = 0;
		}
		else if(line >= l.getLineCount() - 1 && y >= l.getLineTop(line + 1))
		{
			offset = l.getText().length();
		}
		else
		{
			offset = l.getOffsetForHorizontal(line, x);
		}

		edit.setSelection(offset);

	}

	private void initListener1()
	{
		final ScrollView scrollview = (ScrollView) findViewById(R.id.ScrollView01);

		edit.setOnTouchListener(new OnTouchListener()
		{
			//@Override
			public boolean onTouch(View v, MotionEvent event)
			{
				switch(event.getAction())
				{
					case MotionEvent.ACTION_DOWN:
						break;
					case MotionEvent.ACTION_UP:
						if(onScrollFlag)
						{
							int ex = (int) (0.5 + event.getX());
							int ey = (int) (0.5 + event.getY());
							moveCursor(ex, ey);
							setOnScrollFlag(false);
						}
						break;
					case MotionEvent.ACTION_MOVE:
						break;
					case MotionEvent.ACTION_CANCEL:
						break;
				}
				return false;
			}
		});

		scrollview.setOnTouchListener(new OnTouchListener()
		{
			//@Override
			public boolean onTouch(View v, MotionEvent event)
			{

				setOnScrollFlag(true);
				return false;
			}
		});


	}

	private void setOnScrollFlag(boolean flag)
	{
		onScrollFlag = flag;
	}

	// create Menu
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		super.onCreateOptionsMenu(menu);

		menu.add(0, MENUID_CLOSE, 0, R.string.menu_close)
				.setShortcut('0', 'c')
				.setIcon(android.R.drawable.ic_menu_close_clear_cancel);

		menu.add(0, MENUID_SAVE, 0, R.string.menu_save)
				.setShortcut('1', 's')
				.setIcon(android.R.drawable.ic_menu_save);

		menu.add(0, MENUID_SAVE_AS, 0, R.string.menu_save_as)
				.setShortcut('2', 'a')
				.setIcon(android.R.drawable.ic_menu_save);

		return true;
	}

	// メニュー押下時のイベント処理
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item)
	{
		super.onMenuItemSelected(featureId, item);

		switch(item.getItemId())
		{

			case MENUID_CLOSE:        // 終了
				closeFile();
				break;


			case MENUID_SAVE:        // ファイル保存
				saveFile();
				break;

			case MENUID_SAVE_AS:    //名前を付けてファイル保存
				saveFileAsNewFile();
				break;

			default:
				break;
		}
		return true;
	}

	// FileListアクティビティからの戻り
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		ignoreOnResume = true;//onResumeでの処理を無視するためのフラグセット
		switch(requestCode)
		{
			case SHOW_FILELIST_OPEN:    // ファイルオープン
				if(resultCode == RESULT_OK)
				{
					filepath = data.getStringExtra(SelectFileName.INTENT_FILEPATH);
					aFile = new File(filepath);
//				TextEdit.this.setTitle(getString(R.string.app_name) + " - " + aFile.getName());
					setTitleBarText(aFile.getName());
					isEncrypt = data.getBooleanExtra(SelectFileName.INTENT_ENCRYPT, isEncrypt);

					progressDlg = ProgressDialog.show(
							TextEdit.this, null, getString(R.string.proc_open), true, false);
					new FileReadThread().start();
				}
				break;
			case SHOW_FILELIST_SAVE:    // ファイル保存
				if(resultCode == RESULT_OK)
				{
					// ファイル名取得
					final String destfilepath = data.getStringExtra(SelectFileName.INTENT_FILEPATH);
					final boolean destIsEncrypt = data.getBooleanExtra(SelectFileName.INTENT_ENCRYPT, isEncrypt);
					File destFile = new File(destfilepath);
					if(destFile.exists())
					{

						new AlertDialog.Builder(TextEdit.this)
								.setTitle(R.string.alert_overwrite)
								.setCancelable(true)
								.setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener()
								{
									public void onClick(DialogInterface dialog, int whichButton)
									{

										filepath = destfilepath;
										aFile = new File(filepath);
										//TextEdit.this.setTitle(getString(R.string.app_name) + " - " + aFile.getName());
										setTitleBarText(aFile.getName());
										isEncrypt = destIsEncrypt;
										strText = edit.getText().toString();
										doFileWrite();


									}
								})
								.setNegativeButton(R.string.alert_dialog_cancel, new DialogInterface.OnClickListener()
								{
									public void onClick(DialogInterface dialog, int whichButton)
									{
										//Do Nothing.
									}
								})
								.show(); //ダイアログ表示;

					}
					else
					{
						//既にファイルが存在しなければ、上書き保存の確認をせずに保存
						filepath = data.getStringExtra(SelectFileName.INTENT_FILEPATH);
						aFile = new File(filepath);
//					TextEdit.this.setTitle(getString(R.string.app_name) + " - " + aFile.getName());
						setTitleBarText(aFile.getName());
						strText = edit.getText().toString();
						isEncrypt = data.getBooleanExtra(SelectFileName.INTENT_ENCRYPT, isEncrypt);

//					System.out.println("onActivityResult:" + isEncrypt);

						doFileWrite();
					}


				}
				break;
			default:
				break;
		}
	}

	//バックグラウンドになる前にタイマーをリセットしておく
	@Override
	protected void onPause()
	{
		super.onPause();
//        Toast.makeText(this, "onPause()", Toast.LENGTH_SHORT).show();

		mOnPauseTime = new Date().getTime();

		//AutoSave process
		if(autoSaveFlag && !onPauseBeforeSelectSaveFile)
		{
			//message digest 修正されているかどうか確かめるためにdigestを取得して比較する。
			String tmpDigest = getMessageDigest();
			if(tmpDigest == null || !tmpDigest.equals(messageDigest))
			{
				autoSaveFile();
			}

		}
		onPauseBeforeSelectSaveFile = false;
	}

	//復帰したときにタイマーが満了していたらcloseする(暗号化ファイル限定)
	@Override
	protected void onResume()
	{
		super.onResume();
//		System.out.println("onResume:" + isEncrypt);

//        Toast.makeText(this, "onResume()", Toast.LENGTH_SHORT).show();
		if(isEncrypt && !ignoreOnResume)
		{
			long now = new Date().getTime();
			if(now - mOnPauseTime > mResetTimer * 60 * 1000)
			{
				//タイマーが満了していたら
				finish();
			}
		}

		ignoreOnResume = false;//初期化
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event)
	{
		if(event.getAction() == KeyEvent.ACTION_DOWN)
		{
			switch(event.getKeyCode())
			{
				case KeyEvent.KEYCODE_BACK:
					mBackKeyDown = true;
					//back keyのACTION_DOWNの時に処理をする。
					//   			closeFile();//
					return true;

				case KeyEvent.KEYCODE_DPAD_LEFT: // 左キー
					//現在のカーソルの位置が0なら閉じる
					if(edit.getSelectionStart() == 0 && edit.getSelectionEnd() == 0)
					{
						closeFile();
						return true;
					}
					//through to default
				default:
					mBackKeyDown = false;
					break;
			}
		}

		if(event.getAction() == KeyEvent.ACTION_UP)
		{ // キーが離された時
			switch(event.getKeyCode())
			{
				//back keyは無視するACTION_DOWNの時に処理をする。
				case KeyEvent.KEYCODE_BACK: // BACK KEY
					if(mBackKeyDown)
					{
						mBackKeyDown = false;//戻しておく
						closeFile();//
						return true;
					}
					else
					{
						mBackKeyDown = false;
					}
				default:
					mBackKeyDown = false;
					break;
			}
		}
		return super.dispatchKeyEvent(event);
	}

	// ----------------------------------------
	// 以下、ファイル処理スレッド
	// ----------------------------------------

	private void doFileWrite()
	{
		if(isEncrypt)
		{
			//encryptしてbinText
			getPasswordAndEncryptData();
		}
		else
		{
			progressDlg = ProgressDialog.show(
					TextEdit.this, null, getString(R.string.proc_save), true, false);

			try
			{
				setStrTextToBinText();
//				binText = strText.getBytes(charsetName);
				// encodeを指定してbinに変換する。
				new FileWriteThread().start();

			}
			catch(Exception e)
			{
				e.printStackTrace();
				showMessage(getString(R.string.alert_general_error) + "\n" + e.toString());
			}
		}
	}

	/*
	 * データ保存時のパスワード取得とencrypt
	 */
	private void getPasswordAndEncryptData()
	{

		if(PasswordBox.getPassDigest() != null)
		{
			encryptDataAndSave();
		}
		else
		{
			getPasswordForEncryptAndSave();
		}
	}

	private void getPasswordForEncryptAndSave()
	{
		//コンテキストからインフレータを取得
		LayoutInflater inflater = LayoutInflater.from(this);
		//レイアウトXMLからビュー(レイアウト)をインフレート
		final View inputView = inflater.inflate(R.layout.input_pass2, null);

		//ダイアログを構成
//		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		final AlertDialog alertDialog = new AlertDialog.Builder(this)
				.setTitle(R.string.pass_input_text)
				.setCancelable(true)
				.setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener()
				{
					//		    @Override
					public void onClick(DialogInterface dialog, int which)
					{
						//OKボタンが押下された時に入力された文字を設定する
						EditText passEditText = (EditText) inputView.findViewById(R.id.dialog_edittext);
						EditText passEditTextConfirm = (EditText) inputView.findViewById(R.id.dialog_edittext_confirm);
						String pass = passEditText.getText().toString();
						if(pass.equals(passEditTextConfirm.getText().toString()) && pass.length() > 0)
						{
							PasswordBox.setPassword(pass);
							encryptDataAndSave();
						}
						else
						{
							Toast.makeText(TextEdit.this, R.string.password_not_match, Toast.LENGTH_LONG).show();
							getPasswordForEncryptAndSave();
						}

					}
				}).setNegativeButton(R.string.alert_dialog_cancel, new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int whichButton)
					{
						//Do Nothing
					}
				})
				.setView(inputView)
				.create();

		alertDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

		alertDialog.show(); //ダイアログ表示


	}

	private void encryptDataAndSave()
	{
		try
		{
			setStrTextToBinText();
			binText = MyUtil.encrypt(binText, PasswordBox.getPassDigest());

			progressDlg = ProgressDialog.show(
					TextEdit.this, null, getString(R.string.proc_save), true, false);
			new FileWriteThread().start();

		}
		catch(Exception e)
		{
			e.printStackTrace();
			PasswordBox.resetPassword();
			showMessage(getString(R.string.alert_general_error) + "\n" + e.toString());
		}

	}

	/**
	 * input password 1
	 */
	private void getPasswordAndDecryptData()
	{

		LayoutInflater inflater = LayoutInflater.from(this);

		final View inputView = inflater.inflate(R.layout.input_pass, null);


		final AlertDialog alertDialog = new AlertDialog.Builder(this)
				.setTitle(R.string.pass_input_text)
				.setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int which)
					{
						EditText passEditText = (EditText) inputView.findViewById(R.id.dialog_edittext);
						String pass = passEditText.getText().toString();
						if(pass.length() > 0)
						{
							PasswordBox.setPassword(pass);
							decryptData();
							setStringToEditText();
							setSelection();
							messageDigest = getMessageDigest();
						}
						else
						{
							Toast.makeText(TextEdit.this, R.string.password_empty, Toast.LENGTH_LONG).show();
							getPasswordAndDecryptData();
						}

					}
				}).setNegativeButton(R.string.alert_dialog_cancel, new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int whichButton)
					{
						finish();
					}
				}).setOnCancelListener(new DialogInterface.OnCancelListener()
				{
					public void onCancel(DialogInterface dialog)
					{
						finish();
					}
				})
				.setView(inputView)
				.create();

		alertDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

		alertDialog.show(); //ダイアログ表示


	}

	private void decryptData()
	{
		try
		{
			binText = MyUtil.decrypt(binText, PasswordBox.getPassDigest());
			setBinTextToStrText();
		}
		catch(MyUtilException e)
		{
			e.printStackTrace();
			PasswordBox.resetPassword();
			String errorMsg = getString(e.getCode());
			showMessageAndClose(errorMsg);

		}
		catch(Exception e)
		{
			e.printStackTrace();
			PasswordBox.resetPassword();
			showMessageAndClose(getString(R.string.alert_general_error) + "\n" + e.toString());
		}

		isEncrypt = true;

	}

	// close buttomを押したときの処理
	private void closeFile()
	{
		String tmpDigest = getMessageDigest();

		if(tmpDigest != null && tmpDigest.equals(messageDigest))
		{
			// not modified
			strText = "";
			finish();
		}
		else
		{

			if(autoSaveFlag)
			{
				closeAfterSaveFlag = true;
				autoSaveFile();

			}
			else
			{
				new AlertDialog.Builder(TextEdit.this)
						.setTitle(R.string.alert_close_modified_file)
						.setCancelable(true)
						.setPositiveButton(R.string.alert_dialog_yes, new DialogInterface.OnClickListener()
						{
							public void onClick(DialogInterface dialog, int whichButton)
							{
								saveFile();
								closeAfterSaveFlag = true;
							}
						})
						.setNeutralButton(R.string.alert_dialog_no,
								new DialogInterface.OnClickListener()
								{
									public void onClick(DialogInterface dialog, int whichButton)
									{
										strText = "";
										finish();
									}
								})
						.setNegativeButton(R.string.alert_dialog_cancel, new DialogInterface.OnClickListener()
						{
							public void onClick(DialogInterface dialog, int whichButton)
							{
								//Do Nothing.
							}
						})
						.show();
			}
		}

	}

	private void saveFile()
	{
		if(!aFile.isDirectory())
		{
			strText = edit.getText().toString();
			doFileWrite();
		}
		else
		{
			// PASS THROUGH
			saveFileAsNewFile();
		}

	}

	//自動保存用
	// onPause(),closeFile()の中で呼び出される
	private void autoSaveFile()
	{
		if(aFile.isDirectory())
		{
			strText = edit.getText().toString();

			filepath = getFilenameFromHeadLineNonDuplicate();
			aFile = new File(filepath);
			setTitleBarText(aFile.getName());

			doFileWrite();
		}
		else
		{

			strText = edit.getText().toString();
			doFileWrite();
		}
	}

	private void saveFileAsNewFile()
	{

		Intent intent = new Intent(TextEdit.this, SelectFileName.class);

		String param_filepath;
		strText = edit.getText().toString();
		if(aFile.isDirectory())
		{
			String s = getHeadLineForFilename();
			if(s.length() <= 0)
				s = getString(R.string.alert_dialog_newfile);
			param_filepath = filepath + "/" + s + ".txt";
		}
		else
		{
			param_filepath = filepath;
		}

		onPauseBeforeSelectSaveFile = true; //intent for avoid aute save file
		intent.putExtra(SelectFileName.INTENT_MODE, SelectFileName.MODE_SAVE);
		intent.putExtra(SelectFileName.INTENT_FILEPATH, param_filepath);
		intent.putExtra(SelectFileName.INTENT_ENCRYPT, isEncrypt);

		startActivityForResult(intent, SHOW_FILELIST_SAVE);

	}

	private String getHeadLineForFilename()
	{
		String headLine = "";

		//
		if(strText.length() > 0)
		{
			int i = strText.indexOf('\n');
			if(i >= 0)
			{
//				if(i > 32)i=32;//32文字以下にする。
				headLine = strText.substring(0, i);
			}
			else
			{
				headLine = strText;
			}
			headLine = headLine.replaceAll("^[\\s　]*", "").replaceAll("[\\s　]*$", "");
			headLine = headLine.replaceAll("[/:,;*?\"<>|]", ".");
			headLine = headLine.replaceAll("\\\\", ".");
		}
		return headLine;
	}

	private String getFilenameFromHeadLineNonDuplicate()
	{
		String currentDir;
		String extention = ".txt";

		if(aFile.isDirectory())
		{
			currentDir = aFile.getPath();
		}
		else
		{
			currentDir = aFile.getParent();
		}
		String s = getHeadLineForFilename();
		if(s.length() == 0) s = "noTitle";

		if(isEncrypt) extention = ".chi";

		String dstfilename = currentDir + "/" + s + extention;


		File destFile = new File(dstfilename);

		int i = 0;
		while(destFile.exists() && !filepath.equals(dstfilename))
		{
			i++;
			dstfilename = currentDir + "/" + s + "(" + i + ")" + extention;
			destFile = new File(dstfilename);
		}

		return dstfilename;
	}

	private String getMessageDigest()
	{

		try
		{
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.update(edit.getText().toString().getBytes());
			byte[] b = md.digest();

			StringBuilder s = new StringBuilder();
			for(byte aB : b)
			{
				int d = aB;
				d += d < 0 ? 256 : 0; // byte 128-255
				if(d < 16)
				{ //0-15 16
					s.append("0");
				}
				s.append(Integer.toString(d, 16));
			}
			return s.toString();


		}
		catch(Exception e)
		{
			e.printStackTrace();
			Toast.makeText(TextEdit.this, "Fail to get message digest.", Toast.LENGTH_SHORT).show();

		}
		return null;

	}

	private void setBinTextToStrText() throws Exception
	{
		strText = new String(binText, charsetName);

		if(lineBreakSetting == LINEBREAK_AUTO)
		{
			lineBreakSetting = checkLineBreak();
		}

		strText = strText.replaceAll("\r\n", "\n").replaceAll("\r", "\n");


	}

	private void setStrTextToBinText() throws Exception
	{
		if(lineBreakSetting == LINEBREAK_CRLF)
		{
			strText = strText.replaceAll("\n", "\r\n");
		}
		else if(lineBreakSetting == LINEBREAK_CR)
		{
			strText = strText.replaceAll("\n", "\r");
		}

		binText = strText.getBytes(charsetName);


	}

	private int checkLineBreak()
	{
		if(strText.contains("\r\n"))
		{ //CRLF
			return LINEBREAK_CRLF;
		}
		else if(strText.contains("\r"))
		{
			return LINEBREAK_CR;
		}
		else
		{
			return LINEBREAK_LF;
		}
	}

	private void setSelection()
	{
		int length = strText.length();
		if(selStart > length) selStart = length;
		if(selStop > length) selStop = length;
		edit.setSelection(selStart, selStop);

	}

	private void setStringToEditText()
	{
		edit.setText(strText);

		int size_str = strText.length();
		int size_editbox = edit.length();
		if(size_str != size_editbox)
		{
			showMessage(getString(R.string.alert_text_trancated) + "\n" + size_str + " -> " + size_editbox);
		}

	}

	private void setResultForActionGetContent()
	{
		//for ACTION_GET_CONTENT
		Intent intent = getIntent();
		if(intent != null)
		{
			if(Intent.ACTION_GET_CONTENT.equals(intent.getAction()))
			{
				if(aFile != null && aFile.exists())
				{
					intent.setData(Uri.parse("file://" + aFile.getAbsolutePath()));
					setResult(RESULT_OK, intent);
				}
			}
		}
	}

	private void setTitleBarText(String filename)
	{
		TextEdit.this.setTitle(mAppName + " - " + filename);
	}

	private void showMessage(String msg)
	{
		new AlertDialog.Builder(this)
				.setMessage(msg)
				.setNeutralButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int whichButton)
					{
					}
				})
				.show();
	}

	private void showMessageAndClose(String msg)
	{
		new AlertDialog.Builder(this)
				.setMessage(msg)
				.setNeutralButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int whichButton)
					{
						finish();
					}
				})
				.show();
	}

	private class FileReadThread extends Thread
	{
		public void run()
		{

			try
			{
				binText = MyUtil.readTextFile(filepath);
				notifyHandler.post(run_readFinished);
			}
			catch(MyUtilException e)
			{
				e.printStackTrace();
				strText = "";
				ErrorMessage = getString(e.getCode());
				progressDlg.dismiss();
				notifyHandler.post(run_file_acc_err);


			}
			catch(Exception e)
			{
				e.printStackTrace();
				strText = "";
				ErrorMessage = e.toString();
				progressDlg.dismiss();
				notifyHandler.post(run_file_acc_err);
			}

		}

	}

	private class FileWriteThread extends Thread
	{
		public void run()
		{
			try
			{
				MyUtil.writeTextFile(filepath, binText);
				notifyHandler.post(run_writeFinished);
			}
			catch(Exception e)
			{
				e.printStackTrace();
				ErrorMessage = getString(R.string.alert_general_error) + "\n" + e.toString();
				notifyHandler.post(run_file_acc_err);
			}
		}
	}


}