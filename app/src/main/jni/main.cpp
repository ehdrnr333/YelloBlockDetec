#include <jni.h>
#include "com_example_user_yellowblockdetect_MainActivity.h"
#include <string>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>

using namespace cv;
using namespace std;

extern "C"{

// int 형 string 변환
string IntToString(int number)
{
    std::ostringstream convStream;
    convStream << number;
    return convStream.str ();
}

// 포인트 b에 해당하는 각도값 반환
int GetAngleABC(Point a, Point b, Point c)
{
    Point ab = Point( b.x - a.x, b.y - a.y );
    Point cb = Point( b.x - c.x, b.y - c.y );

    float dot = (ab.x * cb.x + ab.y * cb.y);
    float cross = (ab.x * cb.y - ab.y * cb.x);

    float alpha = atan2(cross, dot);

    return (int)floor(alpha * 180.0 / CV_PI + 0.5); // 세점으로부터 각도 반환
}

JNIEXPORT jintArray JNICALL Java_com_example_user_yellowblockdetect_MainActivity_ProcessFrame(
        JNIEnv *env,jobject  instance, jlong matAddrInput,jlong matAddrResult)
{

    Mat &img_input = *(Mat *)matAddrInput;
    Mat &img_result = *(Mat *)matAddrResult;
    Mat img_hsv, img_gray, img_blur;

    int LowH = 20;
    int HighH = 30;

    int LowS = 50;
    int HighS = 255;

    int LowV = 50;
    int HighV = 255;

    int conSize = 500;

    int length = 40;
    jintArray intArray = env->NewIntArray(length + 2);
    jint *cintArray = new jint[length + 2];
    for(int i=0; i<length + 2; i++) {
        cintArray[i] = 0;
    }

    int largest_area = 0;
    int largest_contour_index = 0;


    // < 1.블러 처리 >
    //img_result = img_input.clone();
   //blur( img_result(Range(0, 100), Range(0, 100)), img_blur, Size( 7, 7 ) );
    blur( img_input, img_blur, Size( 7, 7 ) );
//    GaussianBlur(img_input, img_blur, Size(7, 7), 0, 0);
//    medianBlur(img_input, img_blur, 7);
    // </ 1.블러 처리 > => img_blur


    // < 2.노란색 추출 >
    cvtColor(img_blur, img_hsv, COLOR_RGB2HSV);
    inRange(img_hsv, Scalar(LowH, LowS, LowV), Scalar(HighH, HighS, HighV), img_gray);
    // </ 2.노란색 추출 > => img_gray


    // < 3.형태 추출 >
    vector<Point2f> approx;
    Rect bounding_rect;
    vector<vector<Point> > contours;

    //findContours( img_gray, contours, hierarchy,CV_RETR_CCOMP, CV_CHAIN_APPROX_SIMPLE ); // Find the contours in the image
    findContours(img_gray, contours, RETR_LIST, CHAIN_APPROX_SIMPLE);
    // </ 3.형태 추출 > => contours

    if(contours.size() != 0) {

        // < 4.크기가 가장 큰 형태 찾기 >
        for (size_t i = 0; i < contours.size(); i++) {
            double a = contourArea(contours[i], false);

            if (a > largest_area && a > conSize) {
                largest_area = a;
                largest_contour_index = i;
                bounding_rect = boundingRect(contours[i]);
            }
        }
        // </ 4.크기가 가장 큰 형태 찾기 > => largest_area,largest_contour_index

        if(largest_area > 0) {

            // < 5.가장 큰 도형의 점을 다각형화 >
            approxPolyDP(Mat(contours[largest_contour_index]),
                         approx,
                         arcLength(Mat(contours[largest_contour_index]), true) * 0.02, true);
            // </ 5.가장 큰 도형의 점을 다각형화 > => approx

            // < 6.다각형 꼭지점을 최종 반환배열에 저장 >
            for (int i = 0; i < length / 2; i++) {
                if (approx.size() <= i) break;
                cintArray[0]++;
                cintArray[i * 2 + 1] = approx[i].x;
                cintArray[i * 2 + 2] = approx[i].y;

                // * img_blur에 꼭지점 좌표를 그린다
                putText(img_blur, '['+IntToString(int(approx[(i) % length].x)) +',' +IntToString(int(approx[(i) % length].y)) + ']', approx[(i) % length], FONT_HERSHEY_SIMPLEX, 0.5, CV_RGB(0, 0, 255), 1, 8);
            }
            // </ 6.다각형 꼭지점을 최종 반환배열에 저장 > => cintArray[0~40]

            // < 7.이미지에 다각형 그리기 >
            for (int i = 0; i < cintArray[0] - 1; i++) {
                Point next = Point(cintArray[(i + 1) * 2 + 1], cintArray[(i + 1) * 2 + 2]),
                        curr = Point(cintArray[i * 2 + 1], cintArray[i * 2 + 2]);
                line(img_blur, curr, next, Scalar(0, 255, 0), 3);
            }
            Point next = Point(cintArray[1], cintArray[2]),
                    curr = Point(cintArray[(cintArray[0] - 1) * 2 + 1],
                                 cintArray[(cintArray[0] - 1) * 2 + 2]);
            line(img_blur, curr, next, Scalar(0, 255, 0), 3);
            // </ 7.이미지에 다각형 그리기 >

            // < 8. 다각형에 근사한 각도추출 >
            Vec4f line_obj;
            fitLine(Mat(contours[largest_contour_index]), line_obj, CV_DIST_L2, 0, 0.01, 0.01);
            int x0 = line_obj[2];
            int y0 = line_obj[3];
            int x1 = x0-200*line_obj[0];
            int y1 = y0-200*line_obj[1];
            line(img_blur, Point(x0, y0), Point(x1, y1), Scalar(100,0,200), 3);
            line(img_blur, Point(x0, y0), Point(x1, y0), Scalar(200,0,100), 3);
            int angle = GetAngleABC(Point(x0, y0), Point(x1, y1), Point(x1, y0));

            cintArray[length+1] = angle;
            // </ 8. 다각형에 근사한 각도추출 > => cintArray[41]
        }
    }


    // < 9. 안드로이드로 반환 데이터 처리 >
    img_result = img_blur;
    env->SetIntArrayRegion(intArray, 0, length + 2, cintArray);
    delete [] cintArray;
    // </ 9. 안드로이드로 반환 데이터 처리 >

    return intArray;
}
}
