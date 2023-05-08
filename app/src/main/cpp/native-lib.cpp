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
Point2d avgRect(const std::vector<Point2f>* corners);
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
cv::Mat objPoints;
cv::Mat cameraMatrix;
cv::Mat distCoeffs;

Mat mtxQ(3,3, CV_32F), mtxR(3,3,CV_32F), rotationMatrix(3, 3, CV_32F);

// tracker
Ptr<TrackerKCF> tracker;
Rect2i boundingBox;
bool hasObject = false;

extern "C" JNIEXPORT jstring JNICALL
Java_com_dapa_camloc_activities_TrackerActivity_stringFromJNI(JNIEnv* env, jobject ) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_dapa_camloc_activities_TrackerActivity_detectMarkers(JNIEnv* env, jobject inst, jlong mat_address, jlong draw_address) {
    Mat &bgra = *(Mat *) mat_address;
    Mat &draw = *(Mat *) draw_address;
    Mat mat;

    cvtColor(bgra, mat, COLOR_BGRA2BGR);
    // rotate(mat, mat, ROTATE_90_CLOCKWISE);
    detector.detectMarkers(mat, markerCorners, markerIds, rejectedCandidates);

    // jobjectArray result = env->NewObjectArray((int)markerIds.size(), env->FindClass("com/dapa/camloc/Marker"), nullptr);
    // __android_log_print(ANDROID_LOG_INFO, TAG, "Found: %d\n", markerIds[0]);
    if(markerIds.empty()) return NAN;

    cv::Vec3d rvec, tvec;
    cv::solvePnP(objPoints, markerCorners[0], cameraMatrix, distCoeffs, rvec, tvec, false, SOLVEPNP_ITERATIVE);

    draw = mat.clone();
    drawFrameAxes(draw, cameraMatrix, distCoeffs, rvec, tvec, 0.1);

    // Rodrigues(rvec, rotationMatrix);
    // auto eulerAngles = cv::RQDecomp3x3(rotationMatrix, mtxR, mtxQ);
    // __android_log_print(ANDROID_LOG_INFO, TAG, "rvec: (%.2f, %.2f, %.2f)\n", rvec[0], rvec[1], rvec[2]);

    return 0;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_dapa_camloc_activities_TrackerActivity_trackMarker(JNIEnv* env, jobject inst, jlong mat_address) {
    Mat &bgra = *(Mat *) mat_address;
    Mat mat;

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

// find center of n points
Point2d avgRect(const std::vector<Point2f>* corners) {
    float x = 0, y = 0;
    for (auto &c : *corners) {
        x += c.x;
        y += c.y;
    }
    return { x / (float)corners->size(), y / (float)corners->size() };
}

// 0-1
float getX(const Mat& frame) {
    float center = (float)boundingBox.x + ((float)boundingBox.width / 2);
    return center / (float)frame.size().width;
}

//
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

    // world coordinates
    float markerLength = 0.1;
    objPoints = Mat(4, 1, CV_32FC3);
    objPoints.ptr<cv::Vec3f>(0)[0] = cv::Vec3f(-markerLength/2.f, markerLength/2.f, 0);
    objPoints.ptr<cv::Vec3f>(0)[1] = cv::Vec3f(markerLength/2.f, markerLength/2.f, 0);
    objPoints.ptr<cv::Vec3f>(0)[2] = cv::Vec3f(markerLength/2.f, -markerLength/2.f, 0);
    objPoints.ptr<cv::Vec3f>(0)[3] = cv::Vec3f(-markerLength/2.f, -markerLength/2.f, 0);
}

