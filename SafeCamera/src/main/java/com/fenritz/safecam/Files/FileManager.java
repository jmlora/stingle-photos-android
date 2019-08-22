package com.fenritz.safecam.Files;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;

import com.fenritz.safecam.Auth.KeyManagement;
import com.fenritz.safecam.Crypto.Crypto;
import com.fenritz.safecam.Crypto.CryptoException;
import com.fenritz.safecam.Db.StingleDbContract;
import com.fenritz.safecam.Db.StingleDbHelper;
import com.fenritz.safecam.Net.HttpsClient;
import com.fenritz.safecam.R;
import com.fenritz.safecam.SafeCameraApplication;
import com.fenritz.safecam.SettingsActivity;
import com.fenritz.safecam.Util.Helpers;
import com.fenritz.safecam.Util.StorageUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileManager {


	static final public String SHARE_CACHE_DIR = "share";


	public static byte[] getAndCacheThumb(Context context, String filename, int folder) throws IOException {

		File cacheDir = new File(context.getCacheDir().getPath() + "/thumbCache");
		File cachedFile = new File(context.getCacheDir().getPath() + "/thumbCache/" + filename);

		if(cachedFile.exists()){
			FileInputStream in = new FileInputStream(cachedFile);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[4096];

			int numRead;
			while ((numRead = in.read(buf)) >= 0) {
				out.write(buf, 0, numRead);
			}
			in.close();
			return out.toByteArray();
		}


		HashMap<String, String> postParams = new HashMap<String, String>();

		postParams.put("token", KeyManagement.getApiToken(context));
		postParams.put("file", filename);
		postParams.put("thumb", "1");
		postParams.put("folder", String.valueOf(folder));
		byte[] encFile = new byte[0];

		try {
			encFile = HttpsClient.getFileAsByteArray(context.getString(R.string.api_server_url) + context.getString(R.string.download_file_path), postParams);
		}
		catch (NoSuchAlgorithmException | KeyManagementException e) {

		}

		if(encFile == null || encFile.length == 0){
			return null;
		}

		byte[] fileBeginning = Arrays.copyOfRange(encFile, 0, Crypto.FILE_BEGGINIG_LEN);
		Log.d("beg", new String(fileBeginning, "UTF-8"));
		if (!new String(fileBeginning, "UTF-8").equals(Crypto.FILE_BEGGINING)) {
			return null;
		}

		if(!cacheDir.exists()){
			cacheDir.mkdirs();
		}


		FileOutputStream out = new FileOutputStream(cachedFile);
		out.write(encFile);
		out.close();

		return encFile;
	}

	public static String getDefaultHomeDir(){
		List<StorageUtils.StorageInfo> storageList = StorageUtils.getStorageList();
		if(storageList.size() > 0){
			return storageList.get(0).path;
		}

		return null;
	}

	public static String getHomeDirParentPath(Context context){
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		String defaultHomeDir = getDefaultHomeDir();

		String currentHomeDir = sharedPrefs.getString("home_folder", defaultHomeDir);
		String customHomeDir = sharedPrefs.getString("home_folder_location", null);

		if(currentHomeDir != null && currentHomeDir.equals(SettingsActivity.CUSTOM_HOME_VALUE)){
			currentHomeDir = customHomeDir;
		}

		return ensureLastSlash(currentHomeDir);
	}

	public static String getHomeDir(Context context) {
		return getHomeDir(context, true);
	}

	public static String getHomeDir(Context context, boolean autoCreateDirs) {
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);

		String homeDirPath = getHomeDirParentPath(context) + sharedPrefs.getString("home_folder_name", context.getString(R.string.default_home_folder_name));

		if(autoCreateDirs && !new File(homeDirPath).exists()){
			createFolders(context);
		}

		return homeDirPath;
	}

	public static String ensureLastSlash(String path){
		if(path != null && !path.endsWith("/")){
			return path + "/";
		}
		return path;
	}

	public static String getThumbsDir(Context context) {
		return getHomeDir(context) + "/" + context.getString(R.string.default_thumb_folder_name);
	}

	public static void createFolders(Context context) {
		String homeDirPath = getHomeDir(context, false);

		File dir = new File(homeDirPath + "/" + context.getString(R.string.default_thumb_folder_name));
		if (!dir.exists() || !dir.isDirectory()) {
			dir.mkdirs();
		}
	}

	public static String findNewFileNameIfNeeded(Context context, String filePath, String fileName) {
		return findNewFileNameIfNeeded(context, filePath, fileName, null);
	}

	public static String findNewFileNameIfNeeded(Context context, String filePath, String fileName, Integer number) {
		if (number == null) {
			number = 1;
		}

		File file = new File(ensureLastSlash(filePath) + fileName);
		if (file.exists()) {
			int lastDotIndex = fileName.lastIndexOf(".");
			String fileNameWithoutExt;
			String originalExtension = "";
			if (lastDotIndex > 0) {
				fileNameWithoutExt = fileName.substring(0, lastDotIndex);
				originalExtension = fileName.substring(lastDotIndex);
			}
			else {
				fileNameWithoutExt = fileName;
			}

			Pattern p = Pattern.compile(".+_\\d{1,3}$");
			Matcher m = p.matcher(fileNameWithoutExt);
			if (m.find()) {
				fileNameWithoutExt = fileNameWithoutExt.substring(0, fileName.lastIndexOf("_"));
			}

			String finalFilaname = fileNameWithoutExt + "_" + String.valueOf(number) + originalExtension;

			return findNewFileNameIfNeeded(context, filePath, finalFilaname, ++number);
		}
		return ensureLastSlash(filePath) + fileName;
	}

	public static void rescanDeletedFile(Context context, File file){
		// Set up the projection (we only need the ID)
		String[] projection = { MediaStore.Images.Media._ID };

		// Match on the file path
		String selection = MediaStore.Images.Media.DATA + " = ?";
		String[] selectionArgs = new String[] { file.getAbsolutePath() };

		// Query for the ID of the media matching the file path
		Uri queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
		ContentResolver contentResolver = context.getContentResolver();
		Cursor c = contentResolver.query(queryUri, projection, selection, selectionArgs, null);
		if (c.moveToFirst()) {
		    // We found the ID. Deleting the item via the content provider will also remove the file
		    long id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
		    Uri deleteUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
		    contentResolver.delete(deleteUri, null, null);
		} else {
		    // File not found in media store DB
		}
		c.close();
	}

	public static void checkIsMainFolderWritable(final Activity activity){
        String homeDir = getHomeDir(activity);

        File homeDirFile = new File(homeDir);

        if(!homeDirFile.exists() || !homeDirFile.canWrite()){
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(activity.getString(R.string.home_folder_problem_title));

            builder.setMessage(activity.getString(R.string.home_folder_problem));
            builder.setPositiveButton(activity.getString(R.string.yes), new DialogInterface.OnClickListener() {

                public void onClick(DialogInterface dialog, int whichButton) {

                    SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
                    String defaultHomeDir = getDefaultHomeDir();

                    sharedPrefs.edit().putString("home_folder", defaultHomeDir).putString("home_folder_location", null).commit();

                    createFolders(activity);
                }
            });
            builder.setNegativeButton(activity.getString(R.string.no), null);
            builder.setCancelable(false);
            AlertDialog dialog = builder.create();
            dialog.show();
        }
    }

	public static boolean requestSDCardPermission(final Activity activity){
		if (activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

			if (activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
				new AlertDialog.Builder(activity)
						.setMessage(activity.getString(R.string.sdcard_perm_explain))
						.setPositiveButton(activity.getString(R.string.ok), new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								activity.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, SafeCameraApplication.REQUEST_SD_CARD_PERMISSION);
							}
						})
						.setNegativeButton(activity.getString(R.string.cancel), new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int which) {
										activity.finish();
									}
								}
						)
						.create()
						.show();

			} else {
				activity.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, SafeCameraApplication.REQUEST_SD_CARD_PERMISSION);
			}
			return false;
		}
		if (activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

			if (activity.shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
				new AlertDialog.Builder(activity)
						.setMessage(activity.getString(R.string.sdcard_perm_explain))
						.setPositiveButton(activity.getString(R.string.ok), new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, SafeCameraApplication.REQUEST_SD_CARD_PERMISSION);
							}
						})
						.setNegativeButton(activity.getString(R.string.cancel), new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int which) {
										activity.finish();
									}
								}
						)
						.create()
						.show();

			} else {
				activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, SafeCameraApplication.REQUEST_SD_CARD_PERMISSION);
			}
			return false;
		}
		return true;
	}

	public static boolean isImageFile(String path) {
		String mimeType = URLConnection.guessContentTypeFromName(path);
		return mimeType != null && mimeType.startsWith("image");
	}

	public static boolean isVideoFile(String path) {
		String mimeType = URLConnection.guessContentTypeFromName(path);
		return mimeType != null && mimeType.startsWith("video");
	}

	public static int getFileType(String path){
		int fileType = Crypto.FILE_TYPE_GENERAL;
		if(isImageFile(path)){
			fileType = Crypto.FILE_TYPE_PHOTO;
		}
		else if(isVideoFile(path)){
			fileType = Crypto.FILE_TYPE_VIDEO;
		}

		return fileType;
	}

	public static int getFileType(Context context, Uri uri){
		String mimeType = context.getContentResolver().getType(uri);

		int fileType = Crypto.FILE_TYPE_GENERAL;
		if(mimeType.startsWith("image")){
			fileType = Crypto.FILE_TYPE_PHOTO;
		}
		else if(mimeType.startsWith("video")){
			fileType = Crypto.FILE_TYPE_VIDEO;
		}

		return fileType;
	}

	public static class ImportFilesAsyncTask extends AsyncTask<Void, Integer, Void> {

		protected Context context;
		protected ArrayList<Uri> uris;
		protected OnFinish onFinish;
		protected ProgressDialog progress;

		public ImportFilesAsyncTask(Context context, ArrayList<Uri> uris, OnFinish onFinish){
			this.context = context;
			this.uris = uris;
			this.onFinish = onFinish;
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();

			progress = Helpers.showProgressDialogWithBar(context, context.getString(R.string.importing_files), uris.size(), null);
		}

		@Override
		protected Void doInBackground(Void... params) {
			int index = 0;
			for (Uri uri : uris) {
				try {
					int fileType = getFileType(context, uri);
					InputStream in = context.getContentResolver().openInputStream(uri);

					Cursor returnCursor = context.getContentResolver().query(uri, null, null, null, null);
					/*
					 * Get the column indexes of the data in the Cursor,
					 * move to the first row in the Cursor, get the data,
					 * and display it.
					 */
					int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
					int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
					returnCursor.moveToFirst();
					String filename = returnCursor.getString(nameIndex);
					long fileSize = returnCursor.getLong(sizeIndex);


					String encFilename = Helpers.getNewEncFilename();
					String encFilePath = getHomeDir(context) + "/" + encFilename;

					FileOutputStream outputStream = new FileOutputStream(encFilePath);

					byte[] fileId = SafeCameraApplication.getCrypto().encryptFile(in, outputStream, filename, fileType, fileSize);

					if(fileType == Crypto.FILE_TYPE_PHOTO) {

						InputStream thumbIn = context.getContentResolver().openInputStream(uri);
						ByteArrayOutputStream bytes = new ByteArrayOutputStream();
						int numRead = 0;
						byte[] buf = new byte[1024];
						while ((numRead = thumbIn.read(buf)) >= 0) {
							bytes.write(buf, 0, numRead);
						}
						thumbIn.close();

						//System.gc();

						Helpers.generateThumbnail(context, bytes.toByteArray(), encFilename, fileId, Crypto.FILE_TYPE_PHOTO);
					}
					else if(fileType == Crypto.FILE_TYPE_VIDEO){

						/*String[] filePathColumn = {MediaStore.Images.Media.DATA};
						Cursor cursor = context.getContentResolver().query(uri, filePathColumn, null, null, null);
						cursor.moveToFirst();
						int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
						String picturePath = cursor.getString(columnIndex);
						cursor.close();

						Bitmap thumb = ThumbnailUtils.createVideoThumbnail(picturePath, MediaStore.Video.Thumbnails.MINI_KIND);
						ByteArrayOutputStream bos = new ByteArrayOutputStream();
						thumb.compress(Bitmap.CompressFormat.PNG, 0, bos);*/
						Bitmap thumb = getVideoThumbnail(context, uri);
						ByteArrayOutputStream bos = new ByteArrayOutputStream();
						thumb.compress(Bitmap.CompressFormat.PNG, 100, bos);

						Helpers.generateThumbnail(context, bos.toByteArray(), encFilename, fileId, Crypto.FILE_TYPE_VIDEO);
					}

					long nowDate = System.currentTimeMillis();
					StingleDbHelper db = new StingleDbHelper(context, StingleDbContract.Files.TABLE_NAME_FILES);
					db.insertFile(encFilename, true, false, StingleDbHelper.INITIAL_VERSION, nowDate, nowDate);
					db.close();
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				catch (CryptoException e) {
					e.printStackTrace();
				}
				publishProgress(index+1);
				index++;
			}

			return null;
		}

		private Bitmap getVideoThumbnail(Context context, Uri uri) throws IllegalArgumentException,
				SecurityException{
			MediaMetadataRetriever retriever = new MediaMetadataRetriever();
			retriever.setDataSource(context,uri);
			return retriever.getFrameAtTime();
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);
			progress.setProgress(values[0]);

		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

			progress.dismiss();

			if(onFinish != null){
				onFinish.onFinish();
			}
		}
	}

	public static void deleteTempFiles(Context context){
		File file = new File(context.getCacheDir().getPath() + "/"+FileManager.SHARE_CACHE_DIR);
		if (file.isDirectory()) {
			File[] files = file.listFiles();
			if (files != null) {
				for (File f : files) {
					if (!f.isDirectory()) {
						f.delete();
					}
				}
			}
		}
	}

	public static abstract class OnFinish{
		public abstract void onFinish();
	}
}
