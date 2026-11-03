// PhotonVision: libphoton-napa.so, the JNI bridge for org.photonvision.jni.NapaDetectorJNI.
//
// NAPA (third_party/napa, by Zachary Wilson, https://github.com/zwatx/NAPA, GPL-3.0-only with a GPL
// section 7(b) credit term: see third_party/napa/NOTICE) on the CPU, one detector handle per pipeline,
// through napa_far (src/napa_far.h, from Spectrum 3847's SpectrumJetson, GPL-3.0). Adapted from
// SpectrumJetson's detector/napa/NapaDetectorJNI.cc without streaming NAPA (its libspectrum-stream.so
// libjpeg-turbo decoder path).
//
// No OpenCV or WPILib link dependency: Java passes the gray Mat's data address, size and row step,
// and gets WPILib AprilTagDetection objects back, with corners in AprilTag's order and OpenCV's pixel
// convention (pixel centres at integer coordinates; WPILib's AprilTag detector reports them 0.5 px
// larger).
//
// Java API (all static, org.photonvision.jni.NapaDetectorJNI):
//   long create(String family)                      0 if the family is unknown
//   void destroy(long handle)
//   void setParams(long handle, int minTagPx, int maxHamming, boolean far, int baseMinTagPx,
//                  int farMinTagPx, double sweepsPerSecond)
//   AprilTagDetection[] detect(long handle, long dataAddr, int width, int height, int step)
//   double[] stats(long handle)  frames, base ms, crops, crop ms, sweeps, sweep ms, far tags
#include <jni.h>

#include <algorithm>
#include <array>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <memory>
#include <string>
#include <vector>

#include "napa_far.h"

namespace {
jclass detectionCls = nullptr;
jmethodID detectionCtor = nullptr;

int64_t NowUs() {
  return std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::steady_clock::now().time_since_epoch())
      .count();
}

napa_far::Detector *Handle(jlong h) { return reinterpret_cast<napa_far::Detector *>(h); }

jobjectArray ToJava(JNIEnv *env, const std::string &family, const std::vector<napa_far::Tag> &tags) {
  jobjectArray arr = env->NewObjectArray(static_cast<jsize>(tags.size()), detectionCls, nullptr);
  if (!arr) return nullptr;
  jstring fam = env->NewStringUTF(family.c_str());
  if (!fam) return nullptr;
  for (size_t i = 0; i < tags.size(); ++i) {
    const auto &t = tags[i];
    const std::array<double, 9> H = t.Homography();
    jdoubleArray harr = env->NewDoubleArray(9);
    jdoubleArray carr = env->NewDoubleArray(8);
    if (!harr || !carr) return nullptr;  // OutOfMemoryError pending; Java sees it on return
    env->SetDoubleArrayRegion(harr, 0, 9, H.data());
    env->SetDoubleArrayRegion(carr, 0, 8, t.p.data());
    // Centre: the homography's image of the tag centre, as AprilTag's det->c.
    const double cx = H[2] / H[8], cy = H[5] / H[8];
    jobject o = env->NewObject(detectionCls, detectionCtor, fam, static_cast<jint>(t.id),
                               static_cast<jint>(t.hamming), static_cast<jfloat>(t.margin), harr, cx, cy, carr);
    if (!o) return nullptr;
    env->SetObjectArrayElement(arr, static_cast<jsize>(i), o);
    env->DeleteLocalRef(o);
    env->DeleteLocalRef(harr);
    env->DeleteLocalRef(carr);
  }
  env->DeleteLocalRef(fam);
  return arr;
}
}  // namespace

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *) {
  JNIEnv *env;
  if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
  // WPILib 2027 is moving AprilTagDetection to org.wpilib; up to now it is edu.wpi.first.apriltag.
  for (const char *name :
       {"edu/wpi/first/apriltag/AprilTagDetection", "org/wpilib/vision/apriltag/AprilTagDetection"}) {
    jclass local = env->FindClass(name);
    if (!local) {
      env->ExceptionClear();
      continue;
    }
    detectionCls = static_cast<jclass>(env->NewGlobalRef(local));
    env->DeleteLocalRef(local);
    break;
  }
  if (!detectionCls) {
    std::fprintf(stderr, "photon-napa: could not find WPILib's AprilTagDetection class\n");
    return JNI_ERR;
  }
  // AprilTagDetection(String family, int id, int hamming, float decisionMargin, double[] homography,
  //                   double centerX, double centerY, double[] corners)
  detectionCtor = env->GetMethodID(detectionCls, "<init>", "(Ljava/lang/String;IIF[DDD[D)V");
  if (!detectionCtor) {
    env->ExceptionClear();
    std::fprintf(stderr, "photon-napa: AprilTagDetection has no (String,int,int,float,double[],double,double,double[]) constructor\n");
    return JNI_ERR;
  }
  return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *) {
  JNIEnv *env;
  if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) return;
  if (detectionCls) env->DeleteGlobalRef(detectionCls);
  detectionCls = nullptr;
}

JNIEXPORT jlong JNICALL Java_org_photonvision_jni_NapaDetectorJNI_create(JNIEnv *env, jclass, jstring family) {
  std::string fam = "tag36h11";
  if (family) {
    const char *c = env->GetStringUTFChars(family, nullptr);
    if (c) {
      fam = c;
      env->ReleaseStringUTFChars(family, c);
    }
  }
  auto d = std::make_unique<napa_far::Detector>(fam);
  if (!d->ok()) return 0;
  return reinterpret_cast<jlong>(d.release());
}

JNIEXPORT void JNICALL Java_org_photonvision_jni_NapaDetectorJNI_destroy(JNIEnv *, jclass, jlong h) {
  delete Handle(h);
}

JNIEXPORT void JNICALL Java_org_photonvision_jni_NapaDetectorJNI_setParams(JNIEnv *, jclass, jlong h,
                                                                           jint minTagPx, jint maxHamming,
                                                                           jboolean far, jint baseMinTagPx,
                                                                           jint farMinTagPx,
                                                                           jdouble sweepsPerSecond) {
  auto *d = Handle(h);
  if (!d) return;
  auto c = d->GetConfig();
  c.min_tag_px = std::clamp<int>(minTagPx, 8, 30000);
  c.max_hamming = std::clamp<int>(maxHamming, 0, 3);
  c.far = far == JNI_TRUE;
  c.base_min_tag_px = std::clamp<int>(baseMinTagPx, 8, 30000);
  c.far_min_tag_px = std::clamp<int>(farMinTagPx, 8, 30000);
  c.sweeps_per_s = std::max(0.0, static_cast<double>(sweepsPerSecond));
  d->SetConfig(c);
}

JNIEXPORT jobjectArray JNICALL Java_org_photonvision_jni_NapaDetectorJNI_detect(JNIEnv *env, jclass, jlong h,
                                                                                jlong data, jint width,
                                                                                jint height, jint step) {
  auto *d = Handle(h);
  if (!d || !data || width <= 0 || height <= 0 || step < width) return nullptr;
  const auto tags = d->Detect(reinterpret_cast<const uint8_t *>(data), width, height, step, NowUs());
  return ToJava(env, d->family(), tags);
}

JNIEXPORT jdoubleArray JNICALL Java_org_photonvision_jni_NapaDetectorJNI_stats(JNIEnv *env, jclass, jlong h) {
  auto *d = Handle(h);
  if (!d) return nullptr;
  const auto c = d->GetCounters();
  const double v[7] = {static_cast<double>(c.frames), c.base_ms,    static_cast<double>(c.crops), c.crop_ms,
                       static_cast<double>(c.sweeps), c.sweep_ms, static_cast<double>(c.far_tags)};
  jdoubleArray arr = env->NewDoubleArray(7);
  if (arr) env->SetDoubleArrayRegion(arr, 0, 7, v);
  return arr;
}

}  // extern "C"
