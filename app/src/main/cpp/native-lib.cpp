#include <jni.h>
#include <string>
#include <opencv2/core/core.hpp>
#include <opencv2/objdetect/objdetect.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/calib3d.hpp>
#include <android/log.h>

#define TAG "native-lib.cpp"

using namespace cv;

// define functions that are called from JNI functions
float getCenter(const std::vector<Point2f>* corners);

// aruco detector setup
std::vector<int> markerIds;
std::vector<std::vector<cv::Point2f>> markerCorners, rejectedCandidates;
cv::aruco::DetectorParameters detectorParams = cv::aruco::DetectorParameters();
cv::aruco::Dictionary dictionary = cv::aruco::getPredefinedDictionary(cv::aruco::DICT_4X4_50);
cv::aruco::ArucoDetector detector(dictionary, detectorParams);

cv::Mat cameraMatrix;
cv::Mat distCoeffs;

Mat mat;

extern "C" JNIEXPORT jfloat JNICALL
Java_com_dapa_camloc_activities_TrackerActivity_trackMarker(JNIEnv* env, jobject inst, jlong mat_address) {
    cvtColor(*(Mat *) mat_address, mat, COLOR_BGRA2BGR);
    detector.detectMarkers(mat, markerCorners, markerIds, rejectedCandidates);

    // not detected
    if(markerIds.empty()) return NAN;
    return getCenter(&markerCorners[0]) / (float)mat.size().width;
}

float getCenter(const std::vector<Point2f>* corners) {
    float mx = 0, sx = MAXFLOAT;
    for (auto &c : *corners) {
        mx = mx > c.x ? mx : c.x;
        sx = sx < c.x ? sx : c.x;
    }
    return (mx + sx) / 2.f;
}

// sillygoofy rotation fuckery
extern "C" JNIEXPORT void JNICALL
Java_com_dapa_camloc_activities_TrackerActivity_setParams(JNIEnv* env, jobject inst, jfloat focalX, jfloat focalY, jfloat cX, jfloat cY) {
    cameraMatrix = (Mat_<double>(3,3) << focalX, 0, cX, 0, focalY, cY, 0, 0, 1);
    distCoeffs = (Mat_<double>(1,5) << 0, 0, 0, 0, 0);
}

