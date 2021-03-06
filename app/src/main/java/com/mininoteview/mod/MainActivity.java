package com.mininoteview.mod;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends ListActivity
{
	private static final int LGM_OPEN = 0;
	private static final int LGM_DELETE = 1;
	private static final int LGM_RENAME = 2;
	private static final int LGM_COPY = 3;
	private static final int LGM_MOVE = 4;
	private static final int LGM_CANCEL = 5;
	private static final int LGM_TOTAL = 6;

	private static final int MENUID_NEW_FILE = 0;
	private static final int MENUID_NEW_FOLDER = 1;
	private static final int MENUID_CLOSE      = 2;
	private static final int MENUID_SETTINGS   = 3;
	private static final int MENUID_TOTAL    = 4;

	private static final int SHOW_TEXT_EDIT = 0;
	private static final int SHOW_SETTINGS = 1;
	private static final int SHOW_FILELIST_COPY = 2;
	private static final int SHOW_FILELIST_MOVE = 3;

	private List<String> directoryEntries = new ArrayList<String>();
	private final String mInitDirName = Environment.getExternalStorageDirectory().getAbsolutePath();
	private File currentDirectory = new File(mInitDirName);
	private boolean showBottomBarFlag = false;
	private View mBottombar = null;
	private ViewGroup mMainlayout = null;

	private int mCurrentPosition = -1;

	private int mCurrentOrder = 1;
	private ImageView mOrderIcon;
	private boolean mBackKeyDown = false;

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);
		setContentView(R.layout.main);

		getListView().setOnItemLongClickListener(new OnItemLongClickListener()
		{
			public boolean onItemLongClick(AdapterView<?> l, View v, int position, long id)
			{
				myLongClick(position);
				return true;
			}
		});

		updateConfig();

		File initdir = new File(mInitDirName);
		if(initdir.exists())
		{
			browseTo(initdir);
		}
		else
		{
			Toast.makeText(this, initdir.getAbsoluteFile() + " " + getString(R.string.alert_initdir_is_not_exist), Toast.LENGTH_LONG).show();
			browseToInitDir();
		}

		mOrderIcon = (ImageView) findViewById(R.id.orderIcon);
		if(mCurrentOrder > 0) mOrderIcon.setImageResource(android.R.drawable.arrow_up_float);
		else mOrderIcon.setImageResource(android.R.drawable.arrow_down_float);

		mOrderIcon.setClickable(true);
		mOrderIcon.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				changeListOrder();
			}
		});

		getListView().setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
		{
			public void onItemSelected(AdapterView<?> parent, View view,
									   int position, long id)
			{
				setCurrentPosition(position);
			}

			public void onNothingSelected(AdapterView<?> parent)
			{
			}
		});
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent)
	{
		super.onActivityResult(requestCode, resultCode, intent);

		switch(requestCode)
		{
			case SHOW_TEXT_EDIT:
				refreshDir();
				break;
			case SHOW_SETTINGS:
				updateConfig();
				break;
			case SHOW_FILELIST_COPY:
				if(resultCode == RESULT_OK)
				{
					final String dstfilepath = intent.getStringExtra(SelectFileName.INTENT_FILEPATH);
					final String srcfilepath = intent.getStringExtra(SelectFileName.INTENT_ORG_FILENAME);
					File dstFile = new File(dstfilepath);
					File srcFile = new File(srcfilepath);
					execCopyFile(srcFile, dstFile);
				}
				break;
			case SHOW_FILELIST_MOVE:
				if(resultCode == RESULT_OK)
				{
					final String dstfilepath = intent.getStringExtra(SelectFileName.INTENT_FILEPATH);
					final String srcfilepath = intent.getStringExtra(SelectFileName.INTENT_ORG_FILENAME);
					File dstFile = new File(dstfilepath);
					File srcFile = new File(srcfilepath);
					execMoveFile(srcFile, dstFile);
				}
				break;
			default:
				break;
		}
	}

	@Override
	public void onDestroy()
	{
		saveConfig();
		//PasswordBox.resetPassword();
		super.onDestroy();

	}

	@Override
	protected void onResume()
	{
		super.onResume();
		refreshDir();
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event)
	{
		if(event.getAction() == KeyEvent.ACTION_DOWN)
		{
			switch(event.getKeyCode())
			{
				case KeyEvent.KEYCODE_MENU:
					myOpenMenu();
					return true;

				case KeyEvent.KEYCODE_DEL:
					return true;

				case KeyEvent.KEYCODE_BACK:

					if(!showBottomBarFlag)
					{
						mBackKeyDown = true;
						return true;
					}
					break;


				case KeyEvent.KEYCODE_DPAD_LEFT:
					upOneLevel();
					return true;
				case KeyEvent.KEYCODE_DPAD_RIGHT:
					//ToDo
					KeyEvent e = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER);
					return super.dispatchKeyEvent(e);

				default:
					mBackKeyDown = false;
					break;
			}
		}

		if(event.getAction() == KeyEvent.ACTION_UP)
		{
			switch(event.getKeyCode())
			{
				case KeyEvent.KEYCODE_DEL:
					upOneLevel();
					return true;
				case KeyEvent.KEYCODE_BACK:
					if(mBackKeyDown)
					{
						mBackKeyDown = false;
						if(!showBottomBarFlag)
						{
							if(currentDirectory.getParent() == null
									|| currentDirectory.toString().equals(mInitDirName))
							{
								//PasswordBox.resetPassword();
								finish();
							}
							else
							{
								upOneLevel();
							}
							return true;
						}
					}
					else
					{
						mBackKeyDown = false;
					}
					break;

				case KeyEvent.KEYCODE_DPAD_RIGHT:
					//ToDo
					KeyEvent e = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER);
					return super.dispatchKeyEvent(e);

				default:
					mBackKeyDown = false;
					break;
			}
		}
		return super.dispatchKeyEvent(event);
	}


	/**
	 * This function browses to the
	 * root-directory of the file-system.
	 */
	private void browseToInitDir()
	{
		browseTo(new File(mInitDirName));
	}

	/**
	 * This function browses up one level
	 * according to the field: currentDirectory
	 */
	private void upOneLevel()
	{
		File parent = currentDirectory.getParentFile();
		if(parent != null)
		{
			browseTo(parent);
		}
	}

	private void browseTo(final File aDirectory)
	{
		if(aDirectory.isDirectory())
		{
			File[] files = aDirectory.listFiles();
			if(files == null)
			{
				Toast.makeText(this, "Unable Access...", Toast.LENGTH_SHORT).show();
				return;
			}
			currentDirectory = aDirectory;
			fill();
		}
		else
		{
			try
			{
				openFile(aDirectory);
			}
			catch(Exception e)
			{
				e.printStackTrace();
				Toast.makeText(this, getString(R.string.alert_cannot_openfile) + ": " + aDirectory, Toast.LENGTH_LONG).show();
			}
		}
	}


	private void fill()
	{
		directoryEntries.clear();
		directoryEntries = MyUtil.fillList(currentDirectory, mCurrentOrder);

		TextView txtDirName = (TextView) findViewById(R.id.txtDirName);
		txtDirName.setText(currentDirectory.getAbsolutePath());

		FileListAdapter adapter = new FileListAdapter(this, directoryEntries);
		setListAdapter(adapter);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id)
	{
		mCurrentPosition = position;
		String selectedFileString = directoryEntries.get(position);
		if(selectedFileString.equals("."))
		{
			browseTo(currentDirectory);
			return;
		}
		if(selectedFileString.equals(".."))
		{
			upOneLevel();
			return;
		}

		File clickedFile;
		clickedFile = new File(currentDirectory.getAbsolutePath()
				+ "/" + directoryEntries.get(position));
		browseTo(clickedFile);

	}

	//	@Override
	protected void myLongClick(int position)
	{

		//mCurrentPosition = position;
		String selectedFileString = directoryEntries.get(position);

		if(!selectedFileString.equals(".") && !selectedFileString.equals(".."))
		{
			File clickedFile;
			if(currentDirectory.getParent() == null)
			{
				clickedFile = new File(currentDirectory.getAbsolutePath()
						+ directoryEntries.get(position));
			}
			else
			{
				clickedFile = new File(currentDirectory.getAbsolutePath()
						+ "/" + directoryEntries.get(position));
			}

			final File file = clickedFile;
			CharSequence[] items = new CharSequence[LGM_TOTAL];
			items[LGM_OPEN] = getText(R.string.action_open);
			items[LGM_DELETE] = getText(R.string.action_delete);
			items[LGM_RENAME] = getText(R.string.action_rename);
			items[LGM_COPY] = getText(R.string.action_copy);
			items[LGM_MOVE] = getText(R.string.action_move);
			items[LGM_CANCEL] = getText(R.string.action_cancel);

			new AlertDialog.Builder(this)
					.setTitle(R.string.longclick_menu_title)
					.setItems(items, new DialogInterface.OnClickListener()
					{
						public void onClick(DialogInterface dialog, int item)
						{
							switch(item)
							{

								case LGM_OPEN:        // Open
									browseTo(file);
									break;

								case LGM_DELETE:        // delete
									deleteFile(file);
									break;

								case LGM_RENAME:        // rename
									renameFile(file);
									break;

								case LGM_COPY:        // copy
									copyFile(file);
									break;

								case LGM_MOVE:        // move
									moveFile(file);
									break;

								case LGM_CANCEL:        // Cancel
									// Do nothing
									break;

								default:
									break;
							}
						}
					})
					.show();

		}

	}

	protected void myOpenMenu()
	{
			CharSequence[] items = new CharSequence[MENUID_TOTAL];
			items[MENUID_NEW_FILE] = getText(R.string.action_new_file);
			items[MENUID_NEW_FOLDER] = getText(R.string.action_new_folder);
			items[MENUID_CLOSE] = getText(R.string.action_close);
			items[MENUID_SETTINGS] = getText(R.string.action_preferences);

			new AlertDialog.Builder(this)
					.setTitle(R.string.longclick_menu_title)
					.setItems(items, new DialogInterface.OnClickListener()
					{
						public void onClick(DialogInterface dialog, int item)
						{
							switch(item)
							{

								case MENUID_NEW_FILE: // new file
									Intent intent = new Intent(MainActivity.this, TextEdit.class);
									intent.putExtra("FILEPATH", currentDirectory.getAbsolutePath());
									startActivity(intent);
									break;

								case MENUID_NEW_FOLDER: // new folder
									createDir();
									break;

								case MENUID_CLOSE: // close
									//PasswordBox.resetPassword();
									finish();
									break;

								case MENUID_SETTINGS: // settings
									Intent intent1 = new Intent(MainActivity.this, Settings.class);
									startActivityForResult(intent1, SHOW_SETTINGS);
									break;

								default:
									break;
							}
						}
					})
					.show();
	}

	private void openFile(File aFile)
	{
		// // FIXME: 13/04/17 This must be simplified...
		String end = aFile.getName().substring(aFile.getName().lastIndexOf(".") + 1, aFile.getName().length()).toLowerCase();
		if(end.equals("txt") || MyUtil.getChiSize(aFile) >= 0)
		{
			Intent intent = new Intent(this, TextEdit.class);
			intent.putExtra("FILEPATH", aFile.getAbsolutePath());

			startActivityForResult(intent, SHOW_TEXT_EDIT);

		}
		else
		{
			// other files...
			// Create an Intent
			Intent intent = new Intent();
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			intent.setAction(android.content.Intent.ACTION_VIEW);
			String type = getMIMEType(aFile);
			intent.setDataAndType(Uri.fromFile(aFile), type);
			startActivity(intent);
		}
	}

	private void deleteFile(File aFile)
	{
		final File file = aFile;
		new AlertDialog.Builder(MainActivity.this)
				.setTitle(getText(R.string.action_delete))
				.setMessage(getText(R.string.alert_confirm_delete) + ": " + file.getName())
				.setCancelable(true)
				.setPositiveButton(R.string.action_ok, new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int whichButton)
					{
						execDeleteFile(file);
					}
				})
				.setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int whichButton)
					{
						//Do Nothing.
					}
				})
				.show();

	}

	private void execDeleteFile(File file)
	{
		FileOperatorTask task = new FileOperatorTask(this);
		task.execute(FileOperatorTask.DELETE_FILE, file.getAbsolutePath());

	}

	private void renameFile(File aFile)
	{
		final File file = aFile;

		LayoutInflater inflater = LayoutInflater.from(this);

		if(mMainlayout == null)
			mMainlayout = (ViewGroup) findViewById(R.id.mainLayout);
		final View inputView = inflater.inflate(R.layout.input_name, mMainlayout, false);
		//final View inputView = inflater.inflate(R.layout.input_name, null);

		final EditText nameEditText = (EditText) inputView.findViewById(R.id.dialog_edittext);
		nameEditText.setText(file.getName());

		int i = file.getName().lastIndexOf('.');
		if(i > -1) nameEditText.setSelection(i);


		String title;
		if(file.isDirectory())
		{
			title = getString(R.string.title_folder_name);
		}
		else
		{
			title = getString(R.string.title_file_name);
		}

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(title)
				.setPositiveButton(R.string.action_ok, new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int which)
					{
						EditText nameEditText = (EditText) inputView.findViewById(R.id.dialog_edittext);
						String name = nameEditText.getText().toString();

						String curDir = currentDirectory.getAbsolutePath();

						if(name.length() > 0)
						{
							try
							{
								if(MyUtil.renameFile(file, new File(curDir + "/" + name)))
								{
									Toast.makeText(MainActivity.this, R.string.toast_rename_file, Toast.LENGTH_SHORT).show();
									refreshDir();
								}
								else
								{
									Toast.makeText(MainActivity.this, R.string.error_operation_failed, Toast.LENGTH_SHORT).show();
								}
							}
							catch(MyUtilException e)
							{
								e.printStackTrace();
								showDialog(getString(e.getCode()));
							}
							catch(Exception e)
							{
								e.printStackTrace();
								showDialog(getString(R.string.alert_general_error) + "\n" + e.toString());
							}

						}
						else
						{
							Toast.makeText(MainActivity.this, R.string.alert_name_empty, Toast.LENGTH_SHORT).show();
							renameFile(file);
						}

					}
				}).setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int whichButton)
			{
				//do nothing
			}
		}).setOnCancelListener(new DialogInterface.OnCancelListener()
		{
			//			  @Override
			public void onCancel(DialogInterface dialog)
			{

			}
		})
				.setView(inputView);

		final AlertDialog dialog = builder.create();
		dialog.show();

		nameEditText.setOnFocusChangeListener(new OnFocusChangeListener()
		{
			public void onFocusChange(View v, boolean hasFocus)
			{
				Window w = dialog.getWindow();
				if(hasFocus && w != null)
				{
					w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
				}
			}
		});

		nameEditText.requestFocus();
	}


	private void createDir()
	{
		LayoutInflater inflater = LayoutInflater.from(this);
		if(mMainlayout == null)
			mMainlayout = (ViewGroup) findViewById(R.id.mainLayout);
		final View inputView = inflater.inflate(R.layout.input_name, mMainlayout, false);
		//final View inputView = inflater.inflate(R.layout.input_name, null);

		EditText nameEditText = (EditText) inputView.findViewById(R.id.dialog_edittext);
		nameEditText.setText(R.string.hint_newfolder);

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.title_folder_name)
				.setPositiveButton(R.string.action_ok, new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int which)
					{
						EditText nameEditText = (EditText) inputView.findViewById(R.id.dialog_edittext);
						String name = nameEditText.getText().toString();

						String curDir = currentDirectory.getAbsolutePath();

						if(name.length() > 0)
						{
							try
							{
								if(MyUtil.createDir(new File(curDir + "/" + name)))
								{
									refreshDir();
								}
								else
								{
									Toast.makeText(MainActivity.this, R.string.error_operation_failed, Toast.LENGTH_SHORT).show();
								}
							}
							catch(MyUtilException e)
							{
								e.printStackTrace();
								showDialog(getString(e.getCode()));
							}
							catch(Exception e)
							{
								e.printStackTrace();
								showDialog(e.toString());
							}

						}
						else
						{
							Toast.makeText(MainActivity.this, R.string.alert_name_empty, Toast.LENGTH_LONG).show();
							createDir();
						}

					}
				}).setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int whichButton)
			{
			}
		}).setOnCancelListener(new DialogInterface.OnCancelListener()
		{
			public void onCancel(DialogInterface dialog)
			{

			}
		})
				.setView(inputView)
				.show();
	}

	private void moveFile(File aFile)
	{
		Intent intent = new Intent(this, SelectFileName.class);

		intent.putExtra(SelectFileName.INTENT_MODE, SelectFileName.MODE_MOVE);
		intent.putExtra(SelectFileName.INTENT_FILEPATH, aFile.getAbsolutePath());

		startActivityForResult(intent, SHOW_FILELIST_MOVE);
	}

	private void execMoveFile(File srcFile, File dstFile)
	{
		FileOperatorTask task = new FileOperatorTask(this);
		task.execute(FileOperatorTask.MOVE_FILE, srcFile.getAbsolutePath(), dstFile.getAbsolutePath());
	}

	private void copyFile(File aFile)
	{
		Intent intent = new Intent(this, SelectFileName.class);

		intent.putExtra(SelectFileName.INTENT_MODE, SelectFileName.MODE_COPY);
		intent.putExtra(SelectFileName.INTENT_FILEPATH, aFile.getAbsolutePath());

		startActivityForResult(intent, SHOW_FILELIST_COPY);
	}

	private void execCopyFile(File srcFile, File dstFile)
	{
		FileOperatorTask task = new FileOperatorTask(this);
		task.execute(FileOperatorTask.COPY_FILE, srcFile.getAbsolutePath(), dstFile.getAbsolutePath());
	}

	private void updateConfig()
	{
		Config.update(this);
		mCurrentOrder = Config.getFileListOrder();

		boolean tempFlag = showBottomBarFlag;
		showBottomBarFlag = Config.getShowButtonsFlag();

		if(showBottomBarFlag && (mBottombar == null || mMainlayout == null))
		{
			mMainlayout = (ViewGroup) findViewById(R.id.mainLayout);
			mBottombar = getLayoutInflater().inflate(R.layout.bottom_bar, mMainlayout, false);
			//mBottombar = getLayoutInflater().inflate(R.layout.bottom_bar, null);

			Button btnUpDir = (Button) mBottombar.findViewById(R.id.LeftButton);
			btnUpDir.setText(R.string.action_updir);
			btnUpDir.setOnClickListener(new View.OnClickListener()
			{
				public void onClick(View v)
				{
					upOneLevel();
				}

			});
			Button btnMenu = (Button) mBottombar.findViewById(R.id.RightButton);
			btnMenu.setText(R.string.action_menu);
			btnMenu.setOnClickListener(new View.OnClickListener()
			{
				public void onClick(View v)
				{
					myOpenMenu();
				}

			});

		}

		if(tempFlag != showBottomBarFlag)
		{

			if(showBottomBarFlag)
			{
				mMainlayout.addView(mBottombar);
			}
			else
			{
				mMainlayout.removeView(mBottombar);
			}
		}

	}

	private void saveConfig()
	{
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		Editor editor = pref.edit();
		editor.putInt(getString(R.string.prefFileListOrderKey), mCurrentOrder);
		editor.apply();
	}


	private void changeListOrder()
	{
		mCurrentOrder *= -1;
		if(mCurrentOrder > 0)
		{
			mOrderIcon.setImageResource(android.R.drawable.arrow_up_float);
		}
		else
		{
			mOrderIcon.setImageResource(android.R.drawable.arrow_down_float);
		}

		if(mCurrentPosition > -1)
		{
			int count = getListView().getAdapter().getCount();
			mCurrentPosition = count - mCurrentPosition - 1;
		}
		refreshDir();
	}


	private void showDialog(String msg)
	{
		new AlertDialog.Builder(this)
				.setMessage(msg)
				.setNeutralButton(R.string.action_ok, new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int whichButton)
					{
					}
				})
				.show();
	}

	private String getMIMEType(File f)
	{
		String end = f.getName().substring(f.getName().lastIndexOf(".") + 1, f.getName().length()).toLowerCase();
		return MimeTypeMap.getSingleton().getMimeTypeFromExtension(end);
	}

	void setCurrentPosition(int position)
	{
		mCurrentPosition = position;
	}

	public void refreshDir()
	{
		if(currentDirectory.isDirectory())
		{
			fill();
		}
	}

}
