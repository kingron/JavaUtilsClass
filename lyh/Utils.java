/**
 * 陆陆大顺的安卓 JAVA 便利包
 * 本包提供最常用的类，工具，方法，极大提高编程效率，只适合安卓下使用
 * 可以扩充本工具类，但只允许使用安卓SDK和Java标准类，不准引入第三方库和类
 * 版权所有, 2019， Kingron@163.com
 */
package lyh;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.DownloadManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 陆陆大顺的工具类
 */
public class Utils {
    public final static String TAG = "LYH.UTILS";
    public final static String OS_NAME = System.getProperty("os.name");
    public final static String FORMAT_DATETIME = "yyyy-MM-dd HH:mm:ss";
    public final static String FORMAT_DATE = "yyyy-MM-dd";
    public final static String FORMAT_TIME = "HH:mm:ss";
    public final static String DIR_DATA = Environment.getDataDirectory().getAbsolutePath();
    public static final long PERIOD_DAY = 1000 * 60 * 60 * 24;
    public static final long PERIOD_HOUR = 1000 * 60 * 60;
    public static final long PERIOD_MINUTE = 1000 * 60;
    public static final long PERIOD_SECOND = 1000;

    private static BroadcastReceiver onDownloadComplete;

    public static class ExecResult {
        public int exitCode;
        public String output;
        public String error;
    }

    public interface IDownloadCallback {
        public void onFinished(long id, String url, String Filename);
    }

    /**
     * 字符串类，实现类似TStringList的功能，注意本类不是线程安全的！
     * 字符串不包含\r，\n等换行符之类，每个字符串一行
     * 可添加、删除、保存到文件、从文件读取、转成字符串，从字符串读取
     */
    public static class StringList extends ArrayList<String> {
        private String separator = "\n";
        public String fileName = "";

        public StringList() {
            super();
            if (isWindows()) {
                separator = "\r\n";
            } else if (isMacOS()) {
                separator = "\r";
            }
        }

        public StringList(String separator) {
            super();
            this.separator = separator;
        }

        /**
         * 转成类似数组定义的格式，例如类似 "[aaa, bbb, ccc]" 的字符串
         *
         * @return
         */
        public String toArrayString() {
            return super.toString();
        }

        /**
         * 插入字符串,index待插入的位置，在首行插入为 insert(0, "ssss");
         *
         * @param index
         * @param s
         * @return
         */
        public int insert(int index, String s) {
            add(index, s);
            return index;
        }

        /**
         * 转成分隔符分隔字符串
         *
         * @return 返回结果字符串
         */
        public String toString() {
            StringBuilder buf = new StringBuilder(4096);
            for (int i = 0; i < size(); i++)
                buf.append(get(i) + separator);
            return buf.toString();
        }

        /**
         * 从字符串转成列表，字符串应该用分隔符分隔
         *
         * @param s
         */
        public void fromString(String s) {
            clear();
            String[] strings = s.split(separator);
            for (int i = 0; i <= strings.length; i++)
                add(strings[i]);
        }

        public boolean saveToFile(String filename) {
            this.fileName = filename;
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(filename);
                String s;
                for (int i = 0; i < size(); i++) {
                    s = get(i) + separator;
                    fos.write(s.getBytes());
                }
                fos.close();
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        /**
         * 保存到最近一次加载或写入的文件当中
         *
         * @return
         */
        public boolean save() {
            boolean result = !fileName.equals("");
            return result ? saveToFile(fileName) : false;
        }

        /**
         * 从文件读取列表
         *
         * @param filename
         * @return
         */
        public boolean loadFromFile(String filename) {
            this.fileName = filename;
            clear();
            try {
                FileInputStream fis = new FileInputStream(filename);
                Scanner scanner = new Scanner(fis);
                scanner.useDelimiter(separator);
                while (scanner.hasNextLine())
                    add(scanner.nextLine());
                fis.close();
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        /**
         * 在列表末尾添加字符串，与add相同
         *
         * @param s
         * @return
         */
        public int push(String s) {
            add(s);
            return size();
        }

        /**
         * 弹出最后加入的字符串
         *
         * @return 返回最后的字符串，并从列表中删除
         */
        public String pop() {
            String s = get(size() - 1);
            remove(size() - 1);
            return s;
        }
    }

    public static class MyExceptionHandler implements Thread.UncaughtExceptionHandler {
        private Context context;
        private String url;

        public MyExceptionHandler(Context context, String url) {
            super();
            this.url = url;
            this.context = context;
        }

        @Override
        public void uncaughtException(Thread thread, Throwable ex) {
            Intent intent = new Intent(context, context.getClass());
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("crash", true);
            PendingIntent restartIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT);

            //退出程序
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            am.set(AlarmManager.RTC, System.currentTimeMillis() + 5000, restartIntent); // 1秒钟后重启应用
            System.gc();
            String s = ex.getMessage() + "\r\n" + stackTraceToString(ex);
            Log.i(TAG, s);
            if (url != null) {
                try {
                    final String fn = DIR_CACHE(context) + "/" + getAppName(context) + ".txt";
                    if (stringToFile(fn, currentDateTime() + "\r\n" + s)) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                httpPostFile(url, fn);
                                deleteFile(fn);
                            }
                        }).start();
                    }
                } catch (Exception e) {
                    Log.i(TAG, e.getMessage());
                }
            }
            SystemClock.sleep(5000);
            System.exit(-1);
        }
    }

    public final static String DIR_CACHE(Context context) {
        return context.getCacheDir().getAbsolutePath();
    }

    /**
     * 把类似 12:34:56 的字符串转成 Time 输出
     * 用于保存时间戳时分秒，可用于数据交换，如Date
     */
    public static Date dateFromString(String s) {
        String[] my = s.split(":");
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(my[0]));
        calendar.set(Calendar.MINUTE, Integer.parseInt(my[1]));
        calendar.set(Calendar.SECOND, Integer.parseInt(my[2]));
        return calendar.getTime();
    }

    /**
     * 压缩文件为.zip文件
     *
     * @param srcFile 待压缩的源文件
     * @param dstFile 压缩后的zip文件路径和文件名
     * @return 成功返回 true ，失败返回 false
     */
    public static boolean zipFile(String srcFile, String dstFile) {
        File file = new File(srcFile);
        if (!file.exists()) return false;

        try {
            file = new File(dstFile);
            ZipOutputStream zipOutputStream = new ZipOutputStream(new CheckedOutputStream(new FileOutputStream(file), new CRC32()));
            zipOutputStream.putNextEntry(new ZipEntry(extractFileName(srcFile))); // 写入文件名到输出的.zip中
            FileInputStream input = new FileInputStream(srcFile);
            byte[] buf = new byte[4096];
            int len = -1;

            while ((len = input.read(buf)) != -1) {
                zipOutputStream.write(buf, 0, len);
            }

            zipOutputStream.flush();
            input.close();
            zipOutputStream.flush();
            zipOutputStream.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 删除指定的文件
     *
     * @param name 待删除的文件名
     * @return 删除成功返回 true ，否则返回 false
     */
    public static boolean deleteFile(String name) {
        File file = new File(name);
        return file.delete();
    }

    /**
     * 在文件末尾添加内容
     *
     * @param fileName
     * @param content
     * @return
     */
    public static boolean appendFile(String fileName, String content) {
        try {
            // 打开一个写文件器，构造函数中的第二个参数true表示以追加形式写文件
            FileWriter writer = new FileWriter(fileName, true);
            writer.write(content);
            writer.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 从文件读取全部内容并返回字符串
     *
     * @param fileName
     * @return
     */
    public static String stringFromFile(String fileName) {
        try {
            // 打开一个写文件器，构造函数中的第二个参数true表示以追加形式写文件
            FileInputStream fis = new FileInputStream(fileName);
            byte[] buf = new byte[fis.available()];
            fis.read(buf);
            fis.close();
            return new String(buf);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 保存字符串到文件
     *
     * @param fileName
     * @param content
     * @return
     */
    public static boolean stringToFile(String fileName, String content) {
        try {
            // 打开一个写文件器，构造函数中的第二个参数true表示以追加形式写文件
            FileWriter writer = new FileWriter(fileName, false);
            writer.write(content);
            writer.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 从文件名全路径里面，提取文件名部分，例如：
     * /abc/def/xyz.txt ==> xyz.txt
     *
     * @param fullPath
     * @return a
     */
    public static String extractFileName(String fullPath) {
        return new File(fullPath).getName();
    }

    /**
     * 从字符串中左边开始获取len个字符
     *
     * @param s
     * @param len
     * @return
     */
    public static String left(String s, int len) {
        return s.substring(0, len);
    }

    /**
     * 从字符串右边开始，获取len个字符
     *
     * @param s
     * @param len
     * @return
     */
    public static String right(String s, int len) {
        return s.substring(s.length() - len, s.length() - 1);
    }

    /**
     * 从流中读取所有内容，并返回String
     *
     * @param in 待读取的流
     * @return 返回流中所有数据的内容字符串
     * @throws IOException
     */
    public static String streamToString(InputStream in) throws IOException {
        StringBuffer out = new StringBuffer();
        byte[] b = new byte[4096];
        for (int n; (n = in.read(b)) != -1; ) {
            out.append(new String(b, 0, n));
        }
        return out.toString();
    }

    /**
     * 以同步模式向服务器发送一个HTTP GET请求
     *
     * @param uri 待请求的URL地址
     * @return 返回服务器的信息
     */
    public static String httpGet(String uri) {
        URL url;
        HttpURLConnection urlConnection = null;
        String result = null;
        try {
            url = new URL(uri);
            urlConnection = (HttpURLConnection) url.openConnection();
            InputStream in = urlConnection.getInputStream();
            result = streamToString(in);
        } catch (Exception e) {
            result = e.getMessage();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
        return result;
    }

    public static boolean downloadFile(String url, String filename) {
        try {
            URL u = new URL(url);
            InputStream is = u.openStream();
            DataInputStream dis = new DataInputStream(is);

            byte[] buffer = new byte[4096];
            int length;

            FileOutputStream fos = new FileOutputStream(new File(filename));
            while ((length = dis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
            return true;
        } catch (Exception se) {
            return false;
        }
    }

    /**
     * 利用系统的文件下载管理器，下载文件，回调用于当完成的时候的处理，用法示例
     * <p>
     * Utils.IDownloadCallback callback = new Utils.IDownloadCallback() {
     *
     * @param context  Activity Context
     * @param uri      要下载的URL地址
     * @param Filename 保存的文件名，请确保有相关的权限访问目录，必须是外部存储目录，系统权限限制！
     * @param callback 文件下载完成时的回调，下载可能成功也可能失败！
     * @return
     * @Override public void onFinished(long id, String url, String Filename) {
     * Log.i(TAG, "下载完成: " + id + "," + url + "==>" + Filename);
     * }
     * };
     * String fn = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + File.separator + "demo.txt"
     * Utils.downloadFile(this, "http://www.abc.com", fn, callback);
     */
    public static long downloadFile(final Context context, final String uri, final String Filename, final IDownloadCallback callback) {
        onDownloadComplete = new BroadcastReceiver() {
            public void onReceive(Context ctxt, Intent intent) {
                context.unregisterReceiver(onDownloadComplete);
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (callback != null) callback.onFinished(id, uri, Filename);
            }
        };
        context.registerReceiver(onDownloadComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(uri));
        request.setDestinationUri(Uri.fromFile(new File(Filename)));
//        request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, Filename);
        return downloadManager.enqueue(request);
    }

    /**
     * 用异步的方式请求一个URL，返回服务器返回的信息
     * 代码立即返回，不处理返回结果的
     *
     * @param uri 待请求的URL地址
     */
    public static void httpGetAsync(final String uri) {
        //开启线程，发送请求
        new Thread(new Runnable() {
            @Override
            public void run() {
                httpGet(uri);
            }
        }).start();
    }

    /**
     * 上传文件到指定的URL地址，返回服务器返回的结果，同步模式
     * 不能再主线程中运行，如果有需要，请使用下面的方式调用
     * new Thread(new Runnable() {
     *
     * @param uri      服务器URL地址，完整路径
     * @param fileName 本地文件名
     * @return 返回服务器响应字符串
     * @Override public void run() {
     * // Do something....
     * }
     * }).start();
     */
    public static String httpPostFile(String uri, String fileName) {
        final String BOUNDARY = "*****";
        final String TWO_HYPHENS = "--";
        final String LINE_END = "\r\n";
        final String HEAD_END = "\r\n\r\n";

        String sName = extractFileName(fileName);

        try {
            URL url = new URL(uri);
            FileInputStream fis = new FileInputStream(fileName);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Connection", "Close");
            connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + BOUNDARY);
            connection.setRequestProperty("File", sName);

            DataOutputStream request = new DataOutputStream(connection.getOutputStream());
            request.writeBytes(TWO_HYPHENS + BOUNDARY + LINE_END);
            request.writeBytes("Content-Disposition: form-data; name=\"File\"; filename=\"" + sName + "\"" + HEAD_END);

            byte[] buf = new byte[4096];
            int len = -1;
            while ((len = fis.read(buf)) != -1) {
                request.write(buf, 0, len);
            }
            request.writeBytes(LINE_END + TWO_HYPHENS + BOUNDARY + TWO_HYPHENS + LINE_END);
            request.flush();
            request.close();
            InputStream is = connection.getInputStream();
            return streamToString(is);
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    /**
     * 从程序的Assets目录下读取对应资源文件
     * webView.loadUrl("file:///android_asset/dir/file.ext");
     *
     * @param context 程序的context，Asset是和程序相关的
     * @param file    文件名，可以包含相对路径
     * @return 返回文件内容
     */
    public static String readAsset(Context context, String file) {
        try {
            InputStream is = context.getAssets().open(file);
            return streamToString(is);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 按给定的启动时间（hh:mm:ss）设置定时任务，如果时间已过，则自动到下一个周期开始
     *
     * @param time   定时时刻，按hh:mm:ss 例如 12:20:35 表示12点20分35秒开始运行，以后每隔指定周期运行
     * @param task   定时的任务
     * @param period 周期间隔，单位毫秒
     * @return
     */
    public static Timer scheduleTask(String time, TimerTask task, long period) {
        Timer timer = new Timer();
        Date date = dateFromString(time);
        long begin = date.getTime();
        long now = new Date().getTime();

        if (now > begin) // 如果当前时间 > 定时开始的时刻，需要调整到下一次开始的时刻开始！
        {
            long diff = now - begin;
            date = addTime(date, period * roundUp(1.0 * diff / period));
        }
        timer.scheduleAtFixedRate(task, date, period);
        return timer;
    }

    /**
     * 返回两个time2 - time1 时间的毫秒差
     *
     * @param time1
     * @param time2
     * @return
     */
    public static long subTime(Date time1, Date time2) {
        return time2.getTime() - time1.getTime();
    }

    /**
     * 日期相加指定的毫秒数
     *
     * @param time     基准时间
     * @param mSeconds 待相加的毫秒数，可以输入负数，负数表示基准时间之前的时间
     * @return
     */
    public static Date addTime(Date time, long mSeconds) {
        return new Date(time.getTime() + mSeconds);
    }

    /**
     * 对浮点数 x 向上取整返回整数
     *
     * @param x
     * @return
     */
    public static long roundUp(double x) {
        return (long) Math.ceil(x);
    }

    /**
     * 以Root权限运行指令，成功返回true，失败返回false，同步模式，等待命令运行完成
     *
     * @param command 待运行的命令
     * @return 返回命令退出码，-1返回失败
     */
    public static int su(String command) {
        Process process = null;
        DataOutputStream os = null;
        boolean result = false;
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit $?\n");
            os.flush();
            process.waitFor();
            return process.exitValue();
        } catch (Exception e) {
            return -1;
        } finally {
            try {
                if (os != null) os.close();
                if (process != null) process.destroy();
            } catch (Exception e) {
            }
        }
    }

    /**
     * 执行命令并返回结果输出字符串，不适合有交互输入的命令，只适合无需人工干预的命令！
     * 警告：exec只能返回命令或错误输出的前4585个字节的内容，如果输出多余此数，会被截断！
     *
     * @param commands
     * @return 异常返回null，否则返回命令结果
     */
    public static ExecResult exec(String[] commands) {
        ExecResult result = new ExecResult();
        Process process = null;
        InputStream is, err;
        try {
            process = Runtime.getRuntime().exec(commands);
            is = process.getInputStream();
            err = process.getErrorStream();
            process.waitFor();
            result.output = streamToString(is);
            result.exitCode = process.exitValue();
            result.error = streamToString(err);
            is.close();
            err.close();
            return result;
        } catch (Exception e) {
            return null;
        } finally {
            if (process != null) process.destroy();
        }
    }

    /**
     * 下载指定的文件并返回内容
     *
     * @param url
     * @return
     */
    public static String wget(String url) {
        //TODO: 未完成
        return "";
    }

    /**
     * 调用系统的wget（busybox）下载指定URL的内容到文件当中
     *
     * @param url  下载的URL地址
     * @param file 保存的文件名
     * @return 成功返回　true，失败返回false
     */
    public static boolean wgetFile(String url, String file) {
        String[] cmd;
        if (new File("/system/bin/wget").exists()) {
            cmd = new String[]{"wget", "-O", file, url};
        } else {
            cmd = new String[]{"busybox", "wget", "-O", file, url};
        }
        try {
            Runtime.getRuntime().exec(cmd);
            return new File(file).exists();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 从文件读取全部内容并返回字符串，默认UTF-8编码
     *
     * @param file    待读取的文件名
     * @param charset 字符编码，默认UTF-8
     * @return
     * @throws IOException
     */
    public static String readFile(File file, String charset) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(file);
        byte[] buffer = new byte[fileInputStream.available()];
        int length = fileInputStream.read(buffer);
        fileInputStream.close();
        return new String(buffer, 0, length, charset == null ? "UTF-8" : charset);
    }

    public static boolean isLinux() {
        return OS_NAME.indexOf("linux") >= 0;
    }

    public static boolean isWindows() {
        return OS_NAME.indexOf("windows") >= 0;
    }

    public static boolean isMacOS() {
        return OS_NAME.indexOf("mac os") >= 0;
    }

    /**
     * 返回程序的版本信息，可以在前后添加额外的字符串
     *
     * @param context
     * @return
     */
    public static String appVersion(Context context) {
        String result = "";
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            result = String.format("v%s(%d, %s)", info.versionName, info.versionCode, formatDateTime(info.lastUpdateTime));
        } catch (Exception e) {
            // Nothing...
        }
        return result;
    }

    public static String formatDateTime(Date date) {
        return new SimpleDateFormat(FORMAT_DATETIME).format(date);
    }

    public static String formatDateTime(String format, Date date) {
        return new SimpleDateFormat(format).format(date);
    }

    public static String formatDateTime(long timestamp) {
        return new SimpleDateFormat(FORMAT_DATETIME).format(new Date(timestamp));
    }

    public static String formatDateTime(String format, long timestamp) {
        return new SimpleDateFormat(format).format(new Date(timestamp));
    }

    public static String currentTime() {
        return new SimpleDateFormat(FORMAT_TIME).format(new Date());
    }

    public static String currentDate() {
        return new SimpleDateFormat(FORMAT_DATE).format(new Date());
    }

    public static String currentDateTime() {
        return new SimpleDateFormat(FORMAT_DATETIME).format(new Date());
    }

    /**
     * 方法描述：判断某一应用是否正在运行
     * Created by cafeting on 2017/2/4.
     *
     * @param context     上下文
     * @param packageName 应用的包名
     * @return true 表示正在运行，false 表示没有运行
     */
    public static boolean isAppActivity(Context context, String packageName) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> list = am.getRunningTasks(100);
        if (list.size() <= 0) {
            return false;
        }
        for (ActivityManager.RunningTaskInfo info : list) {
            if (info.baseActivity.getPackageName().equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    public static String getAppName(Context context) {
        return context.getPackageName();
    }

    /**
     * 获取已安装应用的 uid，-1 表示未安装此应用或程序异常
     *
     * @param context
     * @param packageName
     * @return
     */
    public static int getPackageUid(Context context, String packageName) {
        try {
            ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(packageName, 0);
            if (applicationInfo != null) {
                return applicationInfo.uid;
            }
        } catch (Exception e) {
            return -1;
        }
        return -1;
    }

    /**
     * 判断某一 uid 的程序是否有正在运行的进程，即是否存活
     * Created by cafeting on 2017/2/4.
     *
     * @param context 上下文
     * @param uid     已安装应用的 uid
     * @return true 表示正在运行，false 表示没有运行
     */
    public static boolean isProcessRunning(Context context, int uid) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> runningServiceInfos = am.getRunningServices(200);
        if (runningServiceInfos.size() > 0) {
            for (ActivityManager.RunningServiceInfo appProcess : runningServiceInfos) {
                if (uid == appProcess.uid) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断给定的服务是否在运行
     *
     * @param context
     * @param className
     * @return
     */
    public static boolean isServiceRunning(Context context, String className) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> serviceList = am.getRunningServices(30);
        for (int i = 0; i < serviceList.size(); i++) {
            if (serviceList.get(i).service.getClassName().equals(className)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断异常给定的包名是否在运行，包含两个情况：有界面和无界面（服务）都可以支持
     *
     * @param context
     * @param packageName
     * @return
     */
    public static boolean isAppRunning(Context context, String packageName) {
        int uid = getPackageUid(context, packageName);
        return uid > 0 ? isAppActivity(context, packageName) || isProcessRunning(context, uid) : false;
    }

    /**
     * 十六进制String转byte[]，Hex字符串必须前导0格式，即不足的必须补0对齐
     *
     * @param str
     * @return
     */
    public static byte[] hexStrToByteArray(String str) {
        if (str == null) {
            return null;
        }
        if (str.length() == 0) {
            return new byte[0];
        }
        byte[] byteArray = new byte[str.length() / 2];
        for (int i = 0; i < byteArray.length; i++) {
            String subStr = str.substring(2 * i, 2 * i + 2);
            byteArray[i] = ((byte) Integer.parseInt(subStr, 16));
        }
        return byteArray;
    }

    /**
     * 执行APK的静默安装
     * Manifest中，需要android:sharedUserId="android.uid.system"
     * 并申请android:name="android.permission.INSTALL_PACKAGES"权限
     * 或者有root权限，可以执行su命令
     *
     * @param apkPath 要安装的apk文件的路径
     * @return 安装成功返回true，安装失败返回false。
     */
    public static boolean apkInstall(String apkPath) {
        String[] commands = new String[]{"/system/bin/pm", "install", "-r", "-d", "-t", apkPath};
        boolean result = exec(commands).exitCode == 0;
        if (!result) result = su("/system/bin/pm install -r -d -t " + apkPath) == 0;
        return result;
    }

    /**
     * 卸载指定包名的APK，包名类似 com.abc.xyz
     *
     * @param packageName
     * @return
     */
    public static boolean apkUnInstall(String packageName) {
        String[] commands = new String[]{"/system/bin/pm", "uninstall", "-k", packageName};
        boolean result = exec(commands).exitCode == 0;
        if (!result) result = su("/system/bin/pm install -k " + packageName) == 0;
        return result;
    }

    /**
     * 采用累加和取反的校验方式计算CRC16，MODBus串口通信一般用这个CRC算法
     * 所有字节进行算术累加，抛弃高位，只保留最后单字节，将单字节取反；
     *
     * @param data 需要计算的数据
     * @return 结果
     */
    public static byte Crc(byte[] data) {
        int r = 0;
        for (int i = 0; i < data.length; i++) r += data[i];
        byte b = (byte) (r & 0x00FF);
        return (byte) ~b;
    }

    /**
     * 异常后自动重启APP，如果给定了URL，会自动把异常信息上传给定的URL
     * URL必须是一个支持HTTP POST的REST API，可以用CURL上传文件的地址即可
     * 如果 URL为空，则不上传
     *
     * @param context
     * @param url
     */
    public static void reloadAfterCrash(Context context, String url) {
        MyExceptionHandler catchException = new MyExceptionHandler(context, url);
        Thread.setDefaultUncaughtExceptionHandler(catchException);
    }

    /**
     * 异常的调用堆栈输出为字符串
     *
     * @param e
     * @return
     */
    public static String stackTraceToString(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append(element.toString());
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 重启应用程序
     *
     * @param context       窗体Context
     * @param delayMSeconds 延迟多少秒启动程序
     */
    public static void restartApplication(Context context, long delayMSeconds) {
        Intent intent = new Intent(context, context.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("restart", true);
        PendingIntent restartIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT);

        //退出程序
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.set(AlarmManager.RTC, System.currentTimeMillis() + delayMSeconds, restartIntent); // x秒钟后重启应用
        System.gc();
        System.exit(0);
    }

    public static boolean updateApp(String apkURL, String filename) {
        if (!downloadFile(apkURL, filename)) return false;
        return apkInstall(filename);
    }
}
