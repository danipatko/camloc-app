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

jobject toMarker(JNIEnv *env, int id, Point2d point);
Point2d avgRect(const std::vector<Point2f>* corners);

extern "C" JNIEXPORT jstring JNICALL
Java_com_dapa_camloc_MainActivity_stringFromJNI(JNIEnv* env, jobject ) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_dapa_camloc_MainActivity_detectMarkers(JNIEnv* env, jobject inst, jlong mat_address) {
    Mat &bgra = *(Mat *) mat_address;
    Mat mat;

    cvtColor(bgra, mat, COLOR_BGRA2BGR);
    // rotate(mat, mat, ROTATE_90_CLOCKWISE);
    detector.detectMarkers(mat, markerCorners, markerIds, rejectedCandidates);

    jobjectArray result = env->NewObjectArray((int)markerIds.size(), env->FindClass("com/dapa/camloc/Marker"), nullptr);
    // __android_log_print(ANDROID_LOG_INFO, TAG, "Found: %d\n", markerIds[0]);
    for (int i = 0; i < markerIds.size(); ++i) {
        env->SetObjectArrayElement(result, i, toMarker(env, markerIds[i], avgRect(&markerCorners[i])));
    }

    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_dapa_camloc_MainActivity_drawMarkers(JNIEnv* env, jobject inst, jlong mat_address, jlong draw_address) {
    Mat &bgra = *(Mat *) mat_address;
    Mat* draw = (Mat *) draw_address;

    Mat mat;

    cvtColor(bgra, mat, COLOR_BGRA2BGR);
    *draw = mat.clone();

    detector.detectMarkers(mat, markerCorners, markerIds, rejectedCandidates);

    for (int i = 0; i < markerIds.size(); ++i) {
        cv::drawMarker(*draw, avgRect(&markerCorners[i]), Scalar(255, 0, 0));
    }
}

// converts opencv point to java opencv binding type point
jobject toMarker(JNIEnv *env, int id, Point2d point) {
    jclass cls = env->FindClass("com/dapa/camloc/Marker");
    jmethodID constructor = env->GetMethodID(cls, "<init>", "(IDD)V");
    jobject object = env->NewObject(cls, constructor, id, point.x, point.y);
    return object;
}

Point2d avgRect(const std::vector<Point2f>* corners) {
    float x = 0, y = 0;
    for (auto &c : *corners) {
        x += c.x;
        y += c.y;
    }
    return { x / (float)corners->size(), y / (float)corners->size() };
}