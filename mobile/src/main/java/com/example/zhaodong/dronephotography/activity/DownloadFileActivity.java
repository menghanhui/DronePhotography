package com.example.zhaodong.dronephotography.activity;

import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.AppCompatActivity;

import com.parrot.arsdk.ardatatransfer.ARDATATRANSFER_ERROR_ENUM;
import com.parrot.arsdk.ardatatransfer.ARDataTransferException;
import com.parrot.arsdk.ardatatransfer.ARDataTransferManager;
import com.parrot.arsdk.ardatatransfer.ARDataTransferMedia;
import com.parrot.arsdk.ardatatransfer.ARDataTransferMediasDownloader;
import com.parrot.arsdk.ardatatransfer.ARDataTransferMediasDownloaderCompletionListener;
import com.parrot.arsdk.ardatatransfer.ARDataTransferMediasDownloaderProgressListener;
import com.parrot.arsdk.armedia.ARMediaObject;
import com.parrot.arsdk.arutils.ARUTILS_DESTINATION_ENUM;
import com.parrot.arsdk.arutils.ARUTILS_FTP_TYPE_ENUM;
import com.parrot.arsdk.arutils.ARUtilsException;
import com.parrot.arsdk.arutils.ARUtilsManager;

import java.util.ArrayList;

public class DownloadFileActivity extends AppCompatActivity implements ARDataTransferMediasDownloaderCompletionListener, ARDataTransferMediasDownloaderProgressListener {
    private static final String MEDIA_FOLDER = "internal_000";

    private AsyncTask<Void, Float, ArrayList<ARMediaObject>> getMediaAsyncTask;
    private AsyncTask<Void, Float, Void> getThumbnailAsyncTask;
    private Handler mFileTransferThreadHandler;
    private HandlerThread mFileTransferThread;
    private boolean isRunning = false;
    private boolean isDownloading = false;
    private final Object lock = new Object();

    private ARDataTransferManager dataTransferManager;
    private ARUtilsManager ftpListManager;
    private ARUtilsManager ftpQueueManager;


    //Create the data transfer manager:
    private void createDataTransferManager() {
        ARDATATRANSFER_ERROR_ENUM result = ARDATATRANSFER_ERROR_ENUM.ARDATATRANSFER_OK;
        try
        {
            dataTransferManager = new ARDataTransferManager();
        }
        catch (ARDataTransferException e)
        {
            e.printStackTrace();
            result = ARDATATRANSFER_ERROR_ENUM.ARDATATRANSFER_ERROR;
        }

        if (result == ARDATATRANSFER_ERROR_ENUM.ARDATATRANSFER_OK)
        {
            try
            {
                ftpListManager = new ARUtilsManager();
                ftpQueueManager = new ARUtilsManager();

                ftpListManager.initFtp(context, deviceService, ARUTILS_DESTINATION_ENUM.ARUTILS_DESTINATION_DRONE, ARUTILS_FTP_TYPE_ENUM.ARUTILS_FTP_TYPE_GENERIC);
                ftpQueueManager.initFtp(context, deviceService, ARUTILS_DESTINATION_ENUM.ARUTILS_DESTINATION_DRONE, ARUTILS_FTP_TYPE_ENUM.ARUTILS_FTP_TYPE_GENERIC);
            }
            catch (ARUtilsException e)
            {
                e.printStackTrace();
                result = ARDATATRANSFER_ERROR_ENUM.ARDATATRANSFER_ERROR_FTP;
            }
        }
        if (result == ARDATATRANSFER_ERROR_ENUM.ARDATATRANSFER_OK)
        {
            // direct to external directory
            String externalDirectory = Environment.getExternalStorageDirectory().toString().concat("/ARSDKMedias/");
            try
            {
                dataTransferManager.getARDataTransferMediasDownloader().createMediasDownloader(ftpListManager, ftpQueueManager, MEDIA_FOLDER, externalDirectory);
            }
            catch (ARDataTransferException e)
            {
                e.printStackTrace();
                result = e.getError();
            }
        }

        if (result == ARDATATRANSFER_ERROR_ENUM.ARDATATRANSFER_OK)
        {
            // create a thread for the download to run the download runnable
            mFileTransferThread = new HandlerThread("FileTransferThread");
            mFileTransferThread.start();
            mFileTransferThreadHandler = new Handler(mFileTransferThread.getLooper());
        }

        if (result != ARDATATRANSFER_ERROR_ENUM.ARDATATRANSFER_OK)
        {
            // clean up here because an error happened
        }
    }

    //Get the list of the medias:
    private void fetchMediasList() {
        if (getMediaAsyncTask == null)
        {
            getMediaAsyncTask = new AsyncTask<Void, Float, ArrayList<ARMediaObject>>()
            {
                @Override
                protected ArrayList<ARMediaObject> doInBackground(Void... params)
                {
                    ArrayList<ARMediaObject> mediaList = null;
                    synchronized (lock)
                    {
                        ARDataTransferMediasDownloader mediasDownloader = null;
                        if (dataTransferManager != null)
                        {
                            mediasDownloader = dataTransferManager.getARDataTransferMediasDownloader();
                        }

                        if (mediasDownloader != null)
                        {
                            try
                            {
                                int mediaListCount = mediasDownloader.getAvailableMediasSync(false);
                                mediaList = new ArrayList<>(mediaListCount);
                                for (int i = 0; i < mediaListCount; i++)
                                {
                                    ARDataTransferMedia currentMedia = mediasDownloader.getAvailableMediaAtIndex(i);
                                    final ARMediaObject currentARMediaObject = new ARMediaObject();
                                    currentARMediaObject.updateDataTransferMedia(getResources(), currentMedia);
                                    mediaList.add(currentARMediaObject);
                                }
                            }
                            catch (ARDataTransferException e)
                            {
                                e.printStackTrace();
                                mediaList = null;
                            }
                        }
                    }

                    return mediaList;
                }

                @Override
                protected void onPostExecute(ArrayList<ARMediaObject> arMediaObjects)
                {
                    // Do what you want with the list of media object
                }
            };
        }

        if (getMediaAsyncTask.getStatus() != AsyncTask.Status.RUNNING) {
            getMediaAsyncTask.execute();
        }
    }


    private void fetchThumbnails() {
        if (getThumbnailAsyncTask == null)
        {
            getThumbnailAsyncTask = new AsyncTask<Void, Float, Void>()
            {
                @Override
                protected Void doInBackground(Void... params)
                {
                    synchronized (lock)
                    {
                        ARDataTransferMediasDownloader mediasDownloader = null;
                        if (dataTransferManager != null)
                        {
                            mediasDownloader = dataTransferManager.getARDataTransferMediasDownloader();
                        }

                        if (mediasDownloader != null)
                        {
                            try
                            {
                                // availableMediaListener is a ARDataTransferMediasDownloaderAvailableMediaListener (you can pass YourActivity.this if YourActivity implements this interface)
                                mediasDownloader.getAvailableMediasAsync(availableMediaListener, null);
                            }
                            catch (ARDataTransferException e)
                            {
                                e.printStackTrace();
                            }
                        }
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void param)
                {
                    adapter.notifyDataSetChanged();
                }
            };
        }

        if (getThumbnailAsyncTask.getStatus() != AsyncTask.Status.RUNNING) {
            getThumbnailAsyncTask.execute();
        }
    }

    @Override
    public void didMediaAvailable(Object arg, final ARDataTransferMedia media, final int index)
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                ARMediaObject mediaObject = getMediaAtIndex(index);
                if (mediaObject != null)
                {
                    mediaObject.updateThumbnailWithDataTransferMedia(getResources(), media);
                    // after that, you can retrieve the thumbnail through mediaObject.getThumbnail()
                }
            }
        });
    }


        //Download medias:
    /**
     * Add the medias to the transfer queue and, if needed, start the queue
     * @param mediaToDl list of media index to download
     */
    private void downloadMedias(ArrayList<Integer> mediaToDl)
    {
        ARDataTransferMediasDownloader mediasDownloader = null;
        if (dataTransferManager != null)
        {
            mediasDownloader = dataTransferManager.getARDataTransferMediasDownloader();
        }

        if (mediasDownloader != null)
        {
            for (int i = 0; i < mediaToDl.size(); i++)
            {
                int mediaIndex = mediaToDl.get(i);
                ARDataTransferMedia mediaObject = null;
                try
                {
                    mediaObject = dataTransferManager.getARDataTransferMediasDownloader().getAvailableMediaAtIndex(mediaIndex);
                }
                catch (ARDataTransferException e)
                {
                    e.printStackTrace();
                }

                if (mediaObject != null)
                {
                    try
                    {
                        mediasDownloader.addMediaToQueue(mediaObject, progressListener, null, completeListener, null);
                    }
                    catch (ARDataTransferException e)
                    {
                        e.printStackTrace();
                    }
                }
            }

            if (!isRunning)
            {
                isRunning = true;
                Runnable downloaderQueueRunnable = mediasDownloader.getDownloaderQueueRunnable();
                mFileTransferThreadHandler.post(downloaderQueueRunnable);
            }
        }
        isDownloading = true;
    }

    @Override
    public void didMediaComplete(Object arg, ARDataTransferMedia media, ARDATATRANSFER_ERROR_ENUM error)
    {
        // the media is downloaded
    }

    @Override
    public void didMediaProgress(Object arg, ARDataTransferMedia media, float percent)
    {
        // the media is downloading
    }


    //Cancel downloading
    private void cancelCurrentDownload()
    {
        dataTransferManager.getARDataTransferMediasDownloader().cancelQueueThread();
        isDownloading = false;
        isRunning = false;
    }

    //Clean:
    private void clean()
    {
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                cancelCurrentDownload();

                if (dataTransferManager != null)
                {
                    dataTransferManager.getARDataTransferMediasDownloader().cancelGetAvailableMedias();
                }
                if (getMediaAsyncTask != null && getMediaAsyncTask.getStatus() == AsyncTask.Status.RUNNING)
                {
                    synchronized (lock)
                    {
                        getMediaAsyncTask.cancel(true);
                    }
                }
                if (getThumbnailAsyncTask != null && getThumbnailAsyncTask.getStatus() == AsyncTask.Status.RUNNING)
                {
                    synchronized (lock)
                    {
                        getThumbnailAsyncTask.cancel(true);
                    }
                }

                if (ftpListManager != null)
                {
                    ftpListManager.closeFtp(context, deviceService);
                    ftpListManager.dispose();
                    ftpListManager = null;
                }
                if (ftpQueueManager != null)
                {
                    ftpQueueManager.closeFtp(context, deviceService);
                    ftpQueueManager.dispose();
                    ftpQueueManager = null;
                }
                if (dataTransferManager != null)
                {
                    dataTransferManager.dispose();
                    dataTransferManager = null;
                }

                if (mFileTransferThread != null)
                {
                    mFileTransferThread.quit();
                    mFileTransferThread = null;
                }
            }
        }).start();
    }
}





