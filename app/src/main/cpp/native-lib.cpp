#include <jni.h>
#include <string>
#include <opencv2/core/core.hpp>
#include <opencv2/objdetect/objdetect.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <android/log.h>

#define TAG "native-lib.cpp"

using namespace cv;

std::vector<int> markerIds;
std::vector<std::vector<cv::Point2f>> markerCorners, rejectedCandidates;
cv::aruco::DetectorParameters detectorParams = cv::aruco::DetectorParameters();
cv::aruco::Dictionary dictionary = cv::aruco::getPredefinedDictionary(cv::aruco::DICT_4X4_50);
cv::aruco::ArucoDetector detector(dictionary, detectorParams);

extern "C" JNIEXPORT jstring JNICALL
Java_com_dapa_camloc_MainActivity_stringFromJNI(JNIEnv* env, jobject ) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_dapa_camloc_MainActivity_balls(JNIEnv* env, jobject inst, jlong mat_address) {
    Mat &shid = *(Mat *) mat_address;
    Mat mat;
    cvtColor(shid, mat, COLOR_BGRA2BGR);
//    Mat &draw = *(Mat *) draw_address;
    detector.detectMarkers(mat, markerCorners, markerIds, rejectedCandidates);
//    draw = mat.clone();
    if(!markerIds.empty()) {
        __android_log_print(ANDROID_LOG_INFO, TAG, "Found: %d\n", markerIds[0]);
//        cv::aruco::drawDetectedMarkers(draw, markerCorners, markerIds);
    }

    // __android_log_print(ANDROID_LOG_INFO, TAG, "Mat type is: %d\n", mat.type());

    // cv::cvtColor(mat, gray, COLOR_RGB2GRAY);
}
