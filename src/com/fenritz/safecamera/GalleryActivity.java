package com.fenritz.safecamera;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import javax.crypto.SecretKey;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.fenritz.safecamera.util.AESCrypt;
import com.fenritz.safecamera.util.AESCrypt.CryptoProgress;
import com.fenritz.safecamera.util.DecryptAndShowImage;
import com.fenritz.safecamera.util.Helpers;
import com.fenritz.safecamera.util.MemoryCache;
import com.fenritz.safecamera.widget.CheckableLayout;

public class GalleryActivity extends Activity {

	public MemoryCache memCache = new MemoryCache();

	private final static int MULTISELECT_OFF = 0;
	private final static int MULTISELECT_ON = 1;

	protected static final int REQUEST_DECRYPT = 0;

	protected static final int REQUEST_ENCRYPT = 1;

	protected static final int REQUEST_IMPORT = 2;

	private int multiSelectMode = MULTISELECT_OFF;

	private GridView photosGrid;

	private final ArrayList<File> files = new ArrayList<File>();
	private final ArrayList<File> selectedFiles = new ArrayList<File>();
	private final ArrayList<File> toGenerateThumbs = new ArrayList<File>();
	private GalleryAdapter galleryAdapter;
	
	private GenerateThumbs thumbGenTask;

	private BroadcastReceiver receiver;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature( Window.FEATURE_NO_TITLE );
		setContentView(R.layout.gallery);

		fillFilesList();

		photosGrid = (GridView) findViewById(R.id.photosGrid);
		galleryAdapter = new GalleryAdapter();
		photosGrid.setAdapter(galleryAdapter);

		findViewById(R.id.multi_select).setOnClickListener(multiSelectClick());
		findViewById(R.id.deleteSelected).setOnClickListener(deleteSelectedClick());
		findViewById(R.id.decryptSelected).setOnClickListener(decryptSelectedClick());
		findViewById(R.id.encryptFiles).setOnClickListener(encryptFilesClick());
		findViewById(R.id.import_btn).setOnClickListener(importClick());
		
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
		
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("com.package.ACTION_LOGOUT");
		receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				finish();
			}
		};
		registerReceiver(receiver, intentFilter);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(receiver);
	}
	
	@SuppressWarnings("unchecked")
	private void fillFilesList() {
		if(thumbGenTask != null){
			thumbGenTask.cancel(true);
			thumbGenTask = null;
		}
		
		File dir = new File(Helpers.getHomeDir(this));
		File[] folderFiles = dir.listFiles();

		Arrays.sort(folderFiles, Collections.reverseOrder());

		files.clear();
		for (File file : folderFiles) {
			if (file.getName().endsWith(getString(R.string.file_extension))) {
				files.add(file);
				
				String thumbPath = Helpers.getThumbsDir(GalleryActivity.this) + "/" + file.getName();
				File thumb = new File(thumbPath);
				if(!thumb.exists() || !thumb.isFile()){
					toGenerateThumbs.add(file);
				}
			}
		}
		
		
		thumbGenTask = new GenerateThumbs();
		thumbGenTask.execute(toGenerateThumbs);
	}

	@Override
	protected void onPause() {
		super.onPause();
		
		Helpers.setLockedTime(this);
		
		if(thumbGenTask != null){
			thumbGenTask.cancel(true);
			thumbGenTask = null;
		}
	}
	
	@SuppressWarnings("unchecked")
	@Override
	protected void onResume() {
		super.onResume();
		
		Helpers.checkLoginedState(this);
		Helpers.disableLockTimer(this);
		
		if(thumbGenTask == null){
			thumbGenTask = new GenerateThumbs();
			thumbGenTask.execute(toGenerateThumbs);
		}
	}
	
	private OnClickListener multiSelectClick() {
		return new OnClickListener() {

			public void onClick(View v) {
				if (multiSelectMode == MULTISELECT_OFF) {
					((ImageButton) v).setImageResource(R.drawable.checkbox_checked);
					multiSelectMode = MULTISELECT_ON;
				}
				else {
					((ImageButton) v).setImageResource(R.drawable.checkbox_unchecked);
					multiSelectMode = MULTISELECT_OFF;
					clearMutliSelect();
				}
			}
		};
	}
	
	private OnClickListener importClick() {
		return new OnClickListener() {

			public void onClick(View v) {
				Intent intent = new Intent(getBaseContext(), FileDialog.class);
				intent.putExtra(FileDialog.START_PATH, Environment.getExternalStorageDirectory().getPath());

				// can user select directories or not
				intent.putExtra(FileDialog.CAN_SELECT_FILE, true);
				intent.putExtra(FileDialog.CAN_SELECT_DIR, false);

				// alternatively you can set file filter
				// intent.putExtra(FileDialog.FORMAT_FILTER, new String[] {
				// "png" });

				startActivityForResult(intent, REQUEST_IMPORT);
			}
		};
	}

	private OnClickListener deleteSelectedClick() {
		return new OnClickListener() {

			@SuppressWarnings("unchecked")
			public void onClick(View v) {
				if (multiSelectMode == MULTISELECT_ON) {
					AlertDialog.Builder builder = new AlertDialog.Builder(GalleryActivity.this);
					builder.setMessage(String.format(getString(R.string.confirm_delete_files), String.valueOf(selectedFiles.size())));
					builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int whichButton) {
							new DeleteFiles().execute(selectedFiles);
						}
					});
					builder.setNegativeButton(getString(R.string.no), null);
					AlertDialog dialog = builder.create();
					dialog.show();

				}
			}
		};
	}

	private OnClickListener decryptSelectedClick() {
		return new OnClickListener() {

			public void onClick(View v) {
				if (multiSelectMode == MULTISELECT_ON) {
					Intent intent = new Intent(getBaseContext(), FileDialog.class);
					intent.putExtra(FileDialog.START_PATH, Helpers.getHomeDir(GalleryActivity.this));

					// can user select directories or not
					intent.putExtra(FileDialog.CAN_SELECT_FILE, false);
					intent.putExtra(FileDialog.CAN_SELECT_DIR, true);

					// alternatively you can set file filter
					// intent.putExtra(FileDialog.FORMAT_FILTER, new String[] {
					// "png" });

					startActivityForResult(intent, REQUEST_DECRYPT);
				}
			}
		};
	}

	private OnClickListener encryptFilesClick() {
		return new OnClickListener() {

			public void onClick(View v) {
				Intent intent = new Intent(getBaseContext(), FileDialog.class);
				intent.putExtra(FileDialog.START_PATH, Environment.getExternalStorageDirectory().getPath());

				// can user select directories or not
				intent.putExtra(FileDialog.CAN_SELECT_FILE, true);
				intent.putExtra(FileDialog.CAN_SELECT_DIR, false);

				// alternatively you can set file filter
				// intent.putExtra(FileDialog.FORMAT_FILTER, new String[] {
				// "png" });

				startActivityForResult(intent, REQUEST_ENCRYPT);
			}
		};
	}

	@SuppressWarnings("unchecked")
	@Override
	public synchronized void onActivityResult(final int requestCode, int resultCode, final Intent data) {

		if (resultCode == Activity.RESULT_OK) {

			if (requestCode == REQUEST_DECRYPT) {
				String filePath = data.getStringExtra(FileDialog.RESULT_PATH);
				File destinationFolder = new File(filePath);
				destinationFolder.mkdirs();
				new DecryptFiles(filePath).execute(selectedFiles);
			}
			else if (requestCode == REQUEST_ENCRYPT) {
				String filePath = data.getStringExtra(FileDialog.RESULT_PATH);
				new EncryptFiles().execute(filePath);
			}
			else if (requestCode == REQUEST_IMPORT) {
				final String filePath = data.getStringExtra(FileDialog.RESULT_PATH);
				
				AlertDialog.Builder dialog = new AlertDialog.Builder(this);

				LayoutInflater layoutInflater = LayoutInflater.from(this);
	            final View enterPasswordView = layoutInflater.inflate(R.layout.dialog_import_password, null);

	            dialog.setPositiveButton(getString(R.string.import_btn), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						String importPassword = ((EditText)enterPasswordView.findViewById(R.id.password)).getText().toString();
						Boolean deleteAfterImport = ((CheckBox)enterPasswordView.findViewById(R.id.deleteAfterImport)).isChecked();
						
						HashMap<String, Object> params = new HashMap<String, Object>();
						
						params.put("filePath", filePath);
						params.put("password", importPassword);
						params.put("deleteAfterImport", deleteAfterImport);
						
						new ImportFiles().execute(params);
					}
	            });
	            
	            dialog.setNegativeButton(getString(R.string.cancel), null);
	            
				dialog.setView(enterPasswordView);
				dialog.setTitle(getString(R.string.enter_import_password));
				
				dialog.show();
			}

		}
		else if (resultCode == Activity.RESULT_CANCELED) {
			// Logger.getLogger().log(Level.WARNING, "file not selected");
		}

	}

	private void clearMutliSelect() {
		((ImageButton) findViewById(R.id.multi_select)).setImageResource(R.drawable.checkbox_unchecked);
		multiSelectMode = MULTISELECT_OFF;
		selectedFiles.clear();
		for (int i = 0; i < photosGrid.getChildCount(); i++) {
			((CheckableLayout) photosGrid.getChildAt(i)).setChecked(false);
		}

	}

	private class ImportFiles extends AsyncTask<HashMap<String, Object>, Integer, Integer> {

		private final int STATUS_OK = 0;
		private final int STATUS_FAIL = 1;
		private final int STATUS_CANCEL = 2;
		
		private ProgressDialog progressDialog;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progressDialog = new ProgressDialog(GalleryActivity.this);
			progressDialog.setCancelable(true);
			progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					ImportFiles.this.cancel(false);
				}
			});
			progressDialog.setMessage(getString(R.string.importing_files));
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.show();
		}

		@Override
		protected Integer doInBackground(HashMap<String, Object>... rparams) {
			HashMap<String, Object> params = rparams[0];

			String filePath = (String)params.get("filePath");
			String password = (String)params.get("password");
			Boolean deleteAfterImport = (Boolean)params.get("deleteAfterImport");
			
			File origFile = new File(filePath);
			
			if (origFile.exists() && origFile.isFile()) {
				try {
					SecretKey newKey = Helpers.getAESKey(GalleryActivity.this, password);
					AESCrypt newCrypt = Helpers.getAESCrypt(newKey, GalleryActivity.this);
					
					FileInputStream inputStream = new FileInputStream(origFile);
					progressDialog.setMax((int) inputStream.getChannel().size());

					
					AESCrypt.CryptoProgress progress = new CryptoProgress(inputStream.getChannel().size()) {
						@Override
						public void setProgress(long pCurrent) {
							super.setProgress(pCurrent);
							publishProgress((int)this.getProgress());
						}
					};
					
					byte[] decryptedData = newCrypt.decrypt(inputStream, progress, this);

					if(decryptedData != null){
						String destFilePath = Helpers.getHomeDir(GalleryActivity.this) + "/" + origFile.getName();
						
						// TODO: Add checking for destination file already exists
						FileOutputStream outputStream = new FileOutputStream(destFilePath);
						Helpers.getAESCrypt(GalleryActivity.this).encrypt(decryptedData, outputStream);
						
						if(deleteAfterImport){
							origFile.delete();
						}
						
						return STATUS_OK;
					}
				}
				catch (FileNotFoundException e) {
					e.printStackTrace();
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}

			return STATUS_FAIL;
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();

			this.onPostExecute(STATUS_CANCEL);
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);

			progressDialog.setProgress(values[0]);
		}

		@Override
		protected void onPostExecute(Integer result) {
			super.onPostExecute(result);

			progressDialog.dismiss();
			fillFilesList();
			galleryAdapter.notifyDataSetChanged();
			clearMutliSelect();
			
			switch(result){
				case STATUS_OK:
					Toast.makeText(GalleryActivity.this, getString(R.string.success_import), Toast.LENGTH_LONG).show();
					break;
				case STATUS_FAIL:
					Toast.makeText(GalleryActivity.this, getString(R.string.import_fialed), Toast.LENGTH_LONG).show();
					break;
			}
		}

	}
	
	private class EncryptFiles extends AsyncTask<String, Integer, Void> {

		private ProgressDialog progressDialog;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progressDialog = new ProgressDialog(GalleryActivity.this);
			progressDialog.setCancelable(true);
			progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					EncryptFiles.this.cancel(false);
				}
			});
			progressDialog.setMessage(getString(R.string.encrypting_files));
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.show();
		}

		@Override
		protected Void doInBackground(String... params) {
			File origFile = new File(params[0]);

			if (origFile.exists() && origFile.isFile()) {
				FileInputStream inputStream;
				try {
					inputStream = new FileInputStream(origFile);
					progressDialog.setMax((int) inputStream.getChannel().size());

					String destFileName = origFile.getName() + getString(R.string.file_extension);

					FileOutputStream outputStream = new FileOutputStream(new File(Helpers.getHomeDir(getApplicationContext()), destFileName));

					AESCrypt.CryptoProgress progress = new CryptoProgress(inputStream.getChannel().size()) {
						@Override
						public void setProgress(long pCurrent) {
							super.setProgress(pCurrent);
							publishProgress((int)this.getProgress());
						}
					};

					Helpers.getAESCrypt(GalleryActivity.this).encrypt(inputStream, outputStream, progress, this);
				}
				catch (FileNotFoundException e) {
					e.printStackTrace();
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}

			return null;
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();

			this.onPostExecute(null);
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);

			progressDialog.setProgress(values[0]);
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

			progressDialog.dismiss();
			fillFilesList();
			galleryAdapter.notifyDataSetChanged();
			clearMutliSelect();
		}

	}

	private class DecryptFiles extends AsyncTask<ArrayList<File>, Integer, Void> {

		private ProgressDialog progressDialog;
		private final String destinationFolder;

		public DecryptFiles(String pDestinationFolder) {
			destinationFolder = pDestinationFolder;
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progressDialog = new ProgressDialog(GalleryActivity.this);
			progressDialog.setCancelable(true);
			progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					DecryptFiles.this.cancel(false);
				}
			});
			progressDialog.setMessage(getString(R.string.decrypting_files));
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.show();
		}

		@Override
		protected Void doInBackground(ArrayList<File>... params) {
			ArrayList<File> filesToDecrypt = params[0];

			progressDialog.setMax(filesToDecrypt.size() * 100);

			for (int i = 0; i < filesToDecrypt.size(); i++) {
				File file = filesToDecrypt.get(i);
				if (file.exists() && file.isFile()) {
					String destFileName = file.getName();
					if (destFileName.substring(destFileName.length() - 3).equalsIgnoreCase(getString(R.string.file_extension))) {
						destFileName = destFileName.substring(0, destFileName.length() - 3);
					}

					try {
						FileInputStream inputStream = new FileInputStream(file);
						FileOutputStream outputStream = new FileOutputStream(new File(destinationFolder, destFileName));

						final int currentIteration = i;
						AESCrypt.CryptoProgress progress = new CryptoProgress(inputStream.getChannel().size()) {
							@Override
							public void setProgress(long pCurrent) {
								super.setProgress(pCurrent);
								int progress = this.getProgressPercents();
								int newProgress = progress + (currentIteration * 100);
								publishProgress(newProgress);
							}
						};

						Helpers.getAESCrypt(GalleryActivity.this).decrypt(inputStream, outputStream, progress, this);
					}
					catch (FileNotFoundException e) {
					}
					catch (IOException e) {
					}
				}

				if (isCancelled()) {
					break;
				}
			}

			return null;
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();

			this.onPostExecute(null);
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);

			progressDialog.setProgress(values[0]);
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

			progressDialog.dismiss();
			fillFilesList();
			galleryAdapter.notifyDataSetChanged();
			clearMutliSelect();
		}

	}

	private class DeleteFiles extends AsyncTask<ArrayList<File>, Integer, Void> {

		private ProgressDialog progressDialog;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progressDialog = new ProgressDialog(GalleryActivity.this);
			progressDialog.setCancelable(true);
			progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					DeleteFiles.this.cancel(false);
				}
			});
			progressDialog.setMessage(getString(R.string.deleting_files));
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.show();
		}

		@Override
		protected Void doInBackground(ArrayList<File>... params) {
			ArrayList<File> filesToDelete = params[0];
			progressDialog.setMax(filesToDelete.size());
			for (int i = 0; i < filesToDelete.size(); i++) {
				File file = filesToDelete.get(i);
				if (file.exists() && file.isFile()) {
					file.delete();
				}

				File thumb = new File(Helpers.getThumbsDir(getApplicationContext()) + "/" + file.getName());

				if (thumb.exists() && thumb.isFile()) {
					thumb.delete();
				}

				publishProgress(i + 1);

				if (isCancelled()) {
					break;
				}
			}

			return null;
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();

			this.onPostExecute(null);
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);

			progressDialog.setProgress(values[0]);
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

			progressDialog.dismiss();
			fillFilesList();
			galleryAdapter.notifyDataSetChanged();
			clearMutliSelect();
		}

	}

	public class GalleryAdapter extends BaseAdapter {

		public int getCount() {
			return files.size();
		}

		public Object getItem(int position) {
			return files.get(position);
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			CheckableLayout layout;
			/*if(convertView != null){
				layout = (CheckableLayout)convertView;
				layout.removeAllViews();
			}
			else{*/
				layout = new CheckableLayout(GalleryActivity.this);
				layout.setGravity(Gravity.CENTER);
				layout.setLayoutParams(new GridView.LayoutParams(Integer.valueOf(getString(R.string.thumb_size)), Integer
						.valueOf(getString(R.string.thumb_size))));
			//}

			final File file = files.get(position);

			final CheckableLayout layoutForOnClick = layout;
			OnClickListener onClick = new View.OnClickListener() {
				public void onClick(View v) {
					if (multiSelectMode == MULTISELECT_ON) {
						layoutForOnClick.toggle();
						if (layoutForOnClick.isChecked()) {
							selectedFiles.add(file);
						}
						else {
							selectedFiles.remove(file);
						}
					}
					else {
						Intent intent = new Intent();
						intent.setClass(GalleryActivity.this, ViewImageActivity.class);
						intent.putExtra("EXTRA_IMAGE_PATH", file.getPath());
						startActivity(intent);
					}
				}
			};

			String thumbPath = Helpers.getThumbsDir(GalleryActivity.this) + "/" + file.getName();

			Bitmap image = memCache.get(thumbPath);
			if (image != null) {
				ImageView imageView = new ImageView(GalleryActivity.this);
				imageView.setImageBitmap(image);
				imageView.setOnClickListener(onClick);
				imageView.setPadding(3, 3, 3, 3);
				layout.addView(imageView);
			}
			else {
				File thumb = new File(thumbPath);
				if(thumb.exists() && thumb.isFile()){
					new DecryptAndShowImage(thumbPath, layout, onClick, memCache, false).execute();
				}
				else{
					if(toGenerateThumbs.contains(file)){
						ProgressBar progress = new ProgressBar(GalleryActivity.this);
						layout.addView(progress);
					}
					else{
						ImageView fileImage = new ImageView(GalleryActivity.this);
						fileImage.setImageResource(R.drawable.fileb);
						fileImage.setPadding(3, 3, 3, 3);
						fileImage.setOnClickListener(onClick);
						layout.addView(fileImage);
					}
				}
			}

			return layout;
		}
	}
	
	private class GenerateThumbs extends AsyncTask<ArrayList<File>, Integer, Void> {

		@Override
		protected Void doInBackground(ArrayList<File>... params) {
			
			int i=0;
			while(toGenerateThumbs.size() > 0){
				File file = toGenerateThumbs.get(0);
				
				if (file.exists() && file.isFile()) {
					try {
						FileInputStream inputStream = new FileInputStream(file);
						byte[] decryptedData = Helpers.getAESCrypt(GalleryActivity.this).decrypt(inputStream, null, this);
						
						if(decryptedData != null){
							Helpers.generateThumbnail(GalleryActivity.this, decryptedData, file.getName());
						}
						
						publishProgress(++i);
			
						if (isCancelled()) {
							break;
						}
					}
					catch (FileNotFoundException e) {
						e.printStackTrace();
					}
					toGenerateThumbs.remove(file);
				}
				
			}
			
			return null;
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);
			
			galleryAdapter.notifyDataSetChanged();
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

			galleryAdapter.notifyDataSetChanged();
		}

	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.gallery_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		Intent intent = new Intent();
		switch (item.getItemId()) {
			case R.id.change_password:
				intent.setClass(GalleryActivity.this, ChangePasswordActivity.class);
				startActivity(intent);
				return true;
			case R.id.settings:
				intent.setClass(GalleryActivity.this, SettingsActivity.class);
				startActivity(intent);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

}
