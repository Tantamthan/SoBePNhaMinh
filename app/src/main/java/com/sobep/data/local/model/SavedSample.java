package com.sobep.data.local.model;

/**
 * Kết quả truy vấn: món trong sổ được chép từ món mẫu nào (màn Khám phá).
 * Dùng để hiện nhãn "Đã lưu" và mở đúng món trong sổ.
 */
public class SavedSample {

    // Mã món trong bảng recipes
    public long id;

    // Mã món mẫu trong assets/mon_mau.json, vd "m01"
    public String sourceSampleId;
}
