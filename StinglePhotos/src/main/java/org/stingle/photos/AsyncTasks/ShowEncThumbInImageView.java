package org.stingle.photos.AsyncTasks;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.widget.ImageView;

import org.stingle.photos.Auth.LoginManager;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Crypto.CryptoHelpers;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class ShowEncThumbInImageView extends AsyncTask<Void, Void, Bitmap> {

	private Context context;
	private String filename;
	private ImageView imageView;
	private Integer thumbSize = null;
	private int set = SyncManager.GALLERY;
	private String albumId = null;
	private String headers = null;
	private boolean isRemote = false;
	private OnAsyncTaskFinish onFinish;
	private boolean roundThumbnail;

	public ShowEncThumbInImageView(Context context, String filename, ImageView imageView) {
		this(context, filename, imageView, true);
	}

	public ShowEncThumbInImageView(Context context, String filename, ImageView imageView, boolean roundThumbnail) {
		this.context = context;
		this.filename = filename;
		this.imageView = imageView;
		this.roundThumbnail = roundThumbnail;
	}

	public ShowEncThumbInImageView setThumbSize(int size){
		thumbSize = size;
		return this;
	}
	public ShowEncThumbInImageView setSet(int set){
		this.set = set;
		return this;
	}
	public ShowEncThumbInImageView setAlbumId(String albumId){
		this.albumId = albumId;
		return this;
	}
	public ShowEncThumbInImageView setHeaders(String headers){
		this.headers = headers;
		return this;
	}

	public ShowEncThumbInImageView setIsRemote(boolean isRemote){
		this.isRemote = isRemote;
		return this;
	}

	public ShowEncThumbInImageView setOnFinish(OnAsyncTaskFinish onFinish){
		this.onFinish = onFinish;
		return this;
	}

	@Override
	protected Bitmap doInBackground(Void... params) {
		if(filename == null || !LoginManager.isKeyInMemory()){
			return null;
		}
		try {
			if(thumbSize == null){
				thumbSize = Helpers.getScreenWidthByColumns(context);
			}

			byte[] decryptedData = null;
			if(!isRemote) {
				File fileToDec = new File(FileManager.getThumbsDir(context) + "/" + filename);
				decryptedData = CryptoHelpers.decryptDbFile(context, set, albumId, headers, true, new FileInputStream(fileToDec));
			}
			else{
				byte[] encFile = FileManager.getAndCacheThumb(context, filename, set);

				if (encFile == null || encFile.length == 0) {
					return null;
				}

				decryptedData = CryptoHelpers.decryptDbFile(context, set, albumId, headers, true, encFile);
			}

			if (decryptedData != null) {
				return Helpers.getThumbFromBitmap(Helpers.decodeBitmap(decryptedData, thumbSize), thumbSize);
			}

		} catch (IOException | CryptoException e) {
			e.printStackTrace();
		}
		return null;
	}



	@Override
	protected void onPostExecute(Bitmap bitmap) {
		super.onPostExecute(bitmap);

		if(bitmap != null){
			imageView.setImageBitmap(getCroppedBitmap(bitmap));
			if(onFinish != null){
				onFinish.onFinish();
			}
		}
	}

	public Bitmap getCroppedBitmap(Bitmap bitmap) {
		Bitmap output = Bitmap.createBitmap(bitmap.getWidth(),
				bitmap.getHeight(), Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(output);

		final int color = 0xff424242;
		final Paint paint = new Paint();
		final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());

		paint.setAntiAlias(true);
		canvas.drawARGB(0, 0, 0, 0);
		paint.setColor(color);

		if (roundThumbnail) {
			canvas.drawCircle(bitmap.getWidth() / 2, bitmap.getHeight() / 2,
					bitmap.getWidth() / 2, paint);
			paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
		}

		canvas.drawBitmap(bitmap, rect, rect, paint);

		return output;
	}
}
