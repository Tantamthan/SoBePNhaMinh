plugins {
    alias(libs.plugins.android.application)
    // Firebase: cần file app/google-services.json (tải từ Firebase Console)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.sobep"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.sobep"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        // Sinh class Binding cho mỗi layout XML (vd. ActivityMainBinding)
        viewBinding = true
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)

    // Room: lưu dữ liệu offline bằng SQLite. Dự án viết Java nên dùng annotationProcessor
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    // Lifecycle: ViewModel (giữ dữ liệu khi xoay màn hình) và LiveData (tự cập nhật giao diện)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    // ML Kit nhận dạng chữ (bản kèm sẵn mô hình trong APK): đọc chữ trong ảnh ngay trên máy, không cần mạng
    implementation(libs.mlkit.text.recognition)

    // Firebase Auth (CN13): đăng ký / đăng nhập / đăng xuất bằng email + mật khẩu.
    // Đăng nhập là tuỳ chọn: không đăng nhập app vẫn chạy offline với Room
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)

    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}