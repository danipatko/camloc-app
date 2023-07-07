#include <jni.h>
#include <string>
#include <opencv2/core/core.hpp>
#include <opencv2/objdetect/objdetect.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/calib3d.hpp>
#include <android/log.h>
#include "headers/tracking.hpp"

#define TAG "native-lib.cpp"

using namespace cv;

// define functions that are called from JNI functions
jobject toMarker(JNIEnv *env, int id, Point2d point);
jobject toRect(JNIEnv *env, Rect2i rect);
Rect2i boundingToRect(const std::vector<Point2f>* corners);
void initTracker(const Mat& frame, Rect2i boundingBox);
bool updateTracker(const Mat& frame);
float getX(const Mat& frame);

// aruco detector setup
std::vector<int> markerIds;
std::vector<std::vector<cv::Point2f>> markerCorners, rejectedCandidates;
cv::aruco::DetectorParameters detectorParams = cv::aruco::DetectorParameters();
cv::aruco::Dictionary dictionary = cv::aruco::getPredefinedDictionary(cv::aruco::DICT_4X4_50);
cv::aruco::ArucoDetector detector(dictionary, detectorParams);

// pose estimation
Mat cameraMatrix;
Mat distCoeffs;

// tracker
Ptr<TrackerKCF> tracker;
Rect2i boundingBox;
bool hasObject = false;

Mat mat;
Mat bgra;

extern "C" JNIEXPORT jfloat JNICALL
Java_com_dapa_camloc_activities_TrackerActivity_trackMarker(JNIEnv* env, jobject inst, jlong mat_address) {
    bgra = *(Mat *) mat_address;
    cvtColor(bgra, mat, COLOR_BGRA2BGR);

    // update tracker
    if(hasObject) {
        hasObject = updateTracker(mat);
        return hasObject ? getX(mat) : NAN;
    }
    // detect then init tracker
    detector.detectMarkers(mat, markerCorners, markerIds, rejectedCandidates);
    // not detected
    hasObject = !markerIds.empty();
    if(!hasObject) return NAN;
    // init tracker
    initTracker(mat, boundingToRect(&markerCorners[0]));
    return getX(mat);
}

// ---

void initTracker(const Mat& frame, Rect2i newBoundingBox) {
    tracker = TrackerKCF::create();
    boundingBox = newBoundingBox;
    tracker->init(frame, boundingBox);
}

bool updateTracker(const Mat& frame) {
    return tracker->update(frame, boundingBox);
}

// converts opencv point to marker java class
jobject toMarker(JNIEnv *env, int id, Point2d point) {
    jclass cls = env->FindClass("com/dapa/camloc/Marker");
    jmethodID constructor = env->GetMethodID(cls, "<init>", "(IDD)V");
    jobject object = env->NewObject(cls, constructor, id, point.x, point.y);
    return object;
}

// converts opencv rect to opencv java class
jobject toRect(JNIEnv *env, Rect2i rect) {
    jclass cls = env->FindClass("org/opencv/core/Rect");
    jmethodID constructor = env->GetMethodID(cls, "<init>", "(IIII)V");
    jobject object = env->NewObject(cls, constructor, rect.x, rect.y, rect.width, rect.height);
    return object;
}

// 0-1
float getX(const Mat& frame) {
    float center = (float)boundingBox.x + ((float)boundingBox.width / 2);
    return center / (float)frame.size().width;
}

Rect2i boundingToRect(const std::vector<Point2f>* corners) {
    float mx = 0, sx = MAXFLOAT, my = 0, sy = MAXFLOAT;
    for (auto &c : *corners) {
        mx = mx > c.x ? mx : c.x;
        sx = sx < c.x ? sx : c.x;
        my = my > c.y ? my : c.y;
        sy = sy < c.y ? sy : c.y;
    }
    return {(int)sx, (int)sy, (int)abs(mx - sx), (int)abs(my - sy)};
}

// sillygoofy rotation fuckery
extern "C" JNIEXPORT void JNICALL
Java_com_dapa_camloc_activities_TrackerActivity_setParams(JNIEnv* env, jobject inst, jfloat focalX, jfloat focalY, jfloat cX, jfloat cY) {
    cameraMatrix = (Mat_<double>(3,3) << focalX, 0, cX, 0, focalY, cY, 0, 0, 1);
    distCoeffs = (Mat_<double>(1,5) << 0, 0, 0, 0, 0);
}

