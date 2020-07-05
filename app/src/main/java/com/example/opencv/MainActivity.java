package com.example.opencv;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.sqrt;
import static org.opencv.imgproc.Imgproc.contourArea;

public class MainActivity extends AppCompatActivity {



    // Used to load the 'native-lib' library on application startup.
    static {
        if (!OpenCVLoader.initDebug()) {
//            System.loadLibrary("src/main/jniLibs");
            // Handle initialization error
        }
//        System.loadLibrary("native-lib");
//        System.loadLibrary("jniLibs");
//        System.loadLibrary("src/main/jniLibs");
//        System.loadLibrary("libs");
    }


    private double max_size = 1024;
    private int PICK_IMAGE_REQUEST = 1;
    private ImageView myImageView;
    private Bitmap selectbp;
    private double ie1=0,ie2=0,je1=0,je2=0,l1=0,s1=0,l2=0,s2=0;
    //椭圆中心位置及长短轴
    private double i0=0,j0=0;
    //i0，j0图像中心
    private double w=0;
    //椭圆倾角
    private double PixelSize=3.2e-3;
    private double focalLength=2.5;
    private double centerXofImage=630.4;
    private double centerYofImage=525.6;
    private double r =10;





    //权限
    private static String[] PERMISSIONS_STORAGE = {
//            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,//写权限
            Manifest.permission.CAMERA//照相权限
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        staticLoadCVLibraries();
        myImageView = (ImageView)findViewById(R.id.imageView);
        myImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Button selectImageBtn = (Button)findViewById(R.id.select_btn);
        selectImageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // makeText(MainActivity.this.getApplicationContext(), "start to browser image", Toast.LENGTH_SHORT).show();
                selectImage();
            }

            private void selectImage() {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent,"选择图像..."), PICK_IMAGE_REQUEST);
                //调用应用之外的ACTIVITY
            }
        });

        //华为手机摄像头权限申请
        //用于判断SDK版本是否大于23
        if (Build.VERSION.SDK_INT>= Build.VERSION_CODES.M){
            //检查权限
            int i = ContextCompat.checkSelfPermission(this,PERMISSIONS_STORAGE[0]);
            //如果权限申请失败，则重新申请权限
            if(i!= PackageManager.PERMISSION_GRANTED){
                //重新申请权限函数
                startRequestPermission();
                Log.e("这里","权限请求成功");
            }
        }


        Button processBtn = (Button)findViewById(R.id.process_btn);
        processBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // makeText(MainActivity.this.getApplicationContext(), "hello, image process", Toast.LENGTH_SHORT).show();
                //convertGray();
                FeaturePoint();
            }
        });

    }

    private void staticLoadCVLibraries() {
        boolean load = OpenCVLoader.initDebug();
        if(load) {
            Log.i("CV", "Open CV Libraries loaded...");
        }

    }

    private void convertGray() {
        Mat src = new Mat();
        Mat temp = new Mat();
        Mat dst = new Mat();
        Utils.bitmapToMat(selectbp, src);
        Imgproc.cvtColor(src, temp, Imgproc.COLOR_BGRA2BGR);         //颜色空间转化
        Log.i("CV", "image type:" + (temp.type() == CvType.CV_8UC3));
        Imgproc.cvtColor(temp, dst, Imgproc.COLOR_BGR2GRAY);          //灰度化
        Utils.matToBitmap(dst, selectbp);
        myImageView.setImageBitmap(selectbp);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                Log.d("image-tag", "start to decode selected image now...");
                InputStream input = getContentResolver().openInputStream(uri);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(input, null, options);
                int raw_width = options.outWidth;
                int raw_height = options.outHeight;
                int max = Math.max(raw_width, raw_height);
                int newWidth = raw_width;
                int newHeight = raw_height;
                int inSampleSize = 1;
                if(max > max_size) {
                    newWidth = raw_width / 2;
                    newHeight = raw_height / 2;
                    while((newWidth/inSampleSize) > max_size || (newHeight/inSampleSize) > max_size) {
                        inSampleSize *=2;
                    }
                }

                options.inSampleSize = inSampleSize;
                options.inJustDecodeBounds = false;
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                selectbp = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri), null, options);

                myImageView.setImageBitmap(selectbp);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    private void startRequestPermission(){
        //321为请求码
        ActivityCompat.requestPermissions(this,PERMISSIONS_STORAGE,321);
    }


    private void FeaturePoint()
    {
        Point center;
        Mat gray_img=new Mat(), bin_img=new Mat();
        Mat Image =new Mat();
        Mat element=new Mat();
        Mat closed=new Mat();
        Mat blurred=new Mat();
        Mat edges=new Mat();
        Utils.bitmapToMat(selectbp, Image);



        double ie[] = { 0,0,0 };
        double je[] = { 0,0,0 };//LED在图像中中心的位置
        double l[] = { 0,0,0 };
        double s[] = { 0,0,0 };//LED椭圆长短轴
        double ww[] = { 0,0,0 };//椭圆倾角
        double j0 = Image.rows() / 2;
        double i0 = Image.cols() / 2;//图像中点坐标



        //图像处理
        Imgproc.cvtColor(Image, gray_img, Imgproc.COLOR_BGR2GRAY);
        Imgproc.threshold(gray_img, bin_img, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
        Imgproc.GaussianBlur(bin_img, blurred,new Size(9, 9), 0);
        element=Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(20, 20));
        Imgproc.morphologyEx(bin_img, closed, Imgproc.MORPH_CLOSE, element);
        Scalar p[] = { new Scalar(255, 0, 0), new Scalar(0, 255, 0),new Scalar(0, 0, 255)};
        Imgproc.Canny(closed, edges, 40, 120, 3);
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();// 创建容器，存储轮廓
        Mat hierarchy=new Mat();// 寻找轮廓所需参数
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE);
        int j = 0;
        for (int i = 0; i < contours.size(); i++) {
            RotatedRect m_ellipsetemp;  // fitEllipse返回值的数据类型
            /*if (contours.size() <= 1) {
                continue;
            }*/
            if (contourArea(contours.get(i)) < 1 && contourArea(contours.get(i)) > 100000) {
                continue;
            }
            MatOfPoint2f point2f = new MatOfPoint2f(contours.get(i).toArray());
            m_ellipsetemp = Imgproc.fitEllipse(point2f);  //找到的第一个轮廓，放置到m_ellipsetemp
            Imgproc.ellipse(Image, m_ellipsetemp, p[2]);   //在图像中绘制椭圆
            center = m_ellipsetemp.center;//读取椭圆中心
            Imgproc.drawContours(Image, contours, i, new Scalar(255, 0, 0), 30, 2);//绘制椭圆中心
            ie[j] = center.x - i0;
            je[j] = j0 - center.y;
            l[j] = m_ellipsetemp.size.height / 2;
            s[j] = m_ellipsetemp.size.width / 2;
            ww[j] = m_ellipsetemp.angle / 180;
            j = j + 1;
        }

        //LED1
        double p1 = sqrt(ie[0]*ie[0] + je[0]*je[0] )*PixelSize;
        double H1 = focalLength * r / (l[0]* PixelSize);
        double o1 = H1 / focalLength * p1;
        double d1 = sqrt(o1*o1 + H1 * H1);
        TextView textView1=findViewById(R.id.text1);
        textView1.setText("LED1:"+d1);

        //LED2
        double p2 = sqrt(ie[1] *ie[1] + je[1] *je[1] )*PixelSize;
        double H2 = focalLength * r / (l[1] * PixelSize);
        double o2 = H2 / focalLength * p2;
        double d2 = sqrt(o2*o2 + H2 * H2);
        TextView textView2=findViewById(R.id.text2);
        textView2.setText("LED2:"+d2);

        //LED3
        double p3 = sqrt(ie[2] *ie[2] + je[2] *je[2])*PixelSize;
        double H3 = focalLength * r / (l[2] * PixelSize);
        double o3 = H2 / focalLength * p3;
        double d3 = sqrt(o3*o3 + H3 * H3);
        TextView textView3=findViewById(R.id.text3);
        textView3.setText("LED3:"+d3);

        double x1=330,x2=-330,x3=0;
        double y1=-330,y2=-330,y3=0;
        double x=0,y=0;
        x=2*(y2-y1)*(d1*d1-d2*d2+x2*x2-x1*x1+y2*y2-y1*y1-d1*d1+d3*d3-x3*x3+x1*x1-y3*y3-y1*y1)/4*((x2-x1)*(y3-y1)-(x3-x1)*(y2-y1));
        x=((y1 - y2)*(d1*d1 - d3*d3 - x1*x1 + x3*x3 - y1*y1 + y3*y3))/(2*(x1*y2 - x2*y1 - x1*y3 + x3*y1 + x2*y3 - x3*y2)) - ((y1 - y3)*(d1*d1 - d2*d2 - x1*x1 + x2*x2 - y1*y1 + y2*y2))/(2*(x1*y2 - x2*y1 - x1*y3 + x3*y1 + x2*y3 - x3*y2));
        TextView textView4=findViewById(R.id.text4);
        textView4.setText("x:"+x);


        y= ((x1 - x3)*(d1*d1 - d2*d2 - x1*x1 + x2*x2 - y1*y1 + y2*y2))/(2*(x1*y2 - x2*y1 - x1*y3 + x3*y1 + x2*y3 - x3*y2)) - ((x1 - x2)*(d1*d1 - d3*d3 - x1*x1 + x3*x3 - y1*y1 + y3*y3))/(2*(x1*y2 - x2*y1 - x1*y3 + x3*y1 + x2*y3 - x3*y2));
        TextView textView5=findViewById(R.id.text5);
        textView5.setText("y:"+y);


        Utils.matToBitmap(Image, selectbp);
        myImageView.setImageBitmap(selectbp);






    }

}
