#include <jni.h>
#include "com_example_user_yellowblockdetect_MainActivity.h"

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>

using namespace cv;
using namespace std;

extern "C"{
string IntToString(int number)
{
    std::ostringstream convStream;
    convStream << number;
    return convStream.str ();
}

   JNIEXPORT void JNICALL Java_com_example_user_yellowblockdetect_MainActivity_ConvertRGBtoGray(
            JNIEnv *env,jobject  instance, jlong matAddrInput,jlong matAddrResult)
   {

       Mat &img_input = *(Mat *)matAddrInput;
       Mat &img_result = *(Mat *)matAddrResult;
       Mat img_hsv, img_gray;

       vector<vector<Point> > contours;

       int LowH = 13;//*(255/180);
       int HighH = 27;

       int LowS = 55;
       int HighS = 255;

       int LowV = 0;
       int HighV = 255;

       int filt = 3;
       int conSize = 600;

       img_result = img_input.clone();
       cvtColor(img_input, img_hsv, COLOR_RGB2HSV);
       inRange(img_hsv, Scalar(LowH, LowS, LowV), Scalar(HighH, HighS, HighV), img_gray);

       //morphological opening 작은 점들을 제거
       erode(img_gray, img_gray, getStructuringElement(MORPH_ELLIPSE, Size(filt, filt)));
       dilate(img_gray, img_gray, getStructuringElement(MORPH_ELLIPSE, Size(filt, filt)));
       //morphological closing 영역의 구멍 메우기
       dilate(img_gray, img_gray, getStructuringElement(MORPH_ELLIPSE, Size(filt, filt)));
       erode(img_gray, img_gray, getStructuringElement(MORPH_ELLIPSE, Size(filt, filt)));

       findContours(img_gray, contours, RETR_LIST, CHAIN_APPROX_SIMPLE);

       vector<Point2f> approx;
       for (size_t i = 0; i < contours.size(); i++)
       {
           approxPolyDP(Mat(contours[i]), approx, arcLength(Mat(contours[i]), true)*0.02, true);

           if (fabs(contourArea(Mat(approx))) > conSize)// && isContourConvex(Mat(approx)) )
           {
               int size = approx.size();

               //Contour를 근사화한 직선을 그린다.
               if (size % 2 == 0) {
                   line(img_result, approx[0], approx[approx.size() - 1], Scalar(0, 255, 0), 3);

                   for (int k = 0; k < size - 1; k++)
                       line(img_result, approx[k], approx[k + 1], Scalar(0, 255, 0), 3);

                   //for (int k = 0; k < size; k++)
                      // circle(img_result, approx[k], 3, Scalar(0, 0, 255));
               }
               else {
                   line(img_result, approx[0], approx[approx.size() - 1], Scalar(0, 255, 0), 3);

                   for (int k = 0; k < size - 1; k++)
                       line(img_result, approx[k], approx[k + 1], Scalar(0, 255, 0), 3);

                   //for (int k = 0; k < size; k++)
                      // circle(img_result, approx[k], 3, Scalar(0, 0, 255));
               }

               //모든 코너의 각도를 구한다.
               vector<int> angle;
               for (int k = 0; k < size; k++)
               {
                   Point p0 = approx[k];
                   Point p1 = approx[(k + 1) % size];
                   Point p2 = approx[(k + 2) % size];

                   Point ab; //= { (p1.x - p0.x), (p1.y - p0.y) };
                   ab.x =  (p1.x - p0.x);
                   ab.y = (p1.y - p0.y);
                   Point cb; //= { (p1.x - p2.x), (p1.y - p2.y) };
                   cb.x = (p1.x - p2.x);
                   cb.y = (p1.y - p2.y);

                   int ang = (int)floor(atan2((ab.x * cb.y - ab.y * cb.x), (ab.x * cb.x + ab.y * cb.y)) * 180.0 / CV_PI + 0.5);
                   int leng = sqrt(pow(p1.x - p2.x, 2) + pow(p1.y - p2.y, 2));

                   Point cen; //= { (p1.x - p2.x), (p1.y - p2.y) };
                   cen.x =(p1.x+p2.x)/2;
                   cen.y = (p1.y+p2.y)/2;

                   putText(img_result, IntToString(ang)+'['+IntToString(int(approx[(k + 1) % size].x)) +',' +IntToString(int(approx[(k + 1) % size].y)) + ']', approx[(k + 1) % size], FONT_HERSHEY_SIMPLEX, 0.5, CV_RGB(0, 0, 255), 1, 8);
                   putText(img_result, IntToString(leng), cen, FONT_HERSHEY_SIMPLEX, 0.5, CV_RGB(255, 0, 0), 1, 8);


                   angle.push_back(ang);
               }
           }
       }
   }
}

