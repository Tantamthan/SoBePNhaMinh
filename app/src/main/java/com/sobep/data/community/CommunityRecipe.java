package com.sobep.data.community;

import com.sobep.data.sample.SampleRecipe;

/**
 * CN15: một bài trên tab Cộng đồng (đọc từ Firestore community_recipes/{postId}).
 *
 * Kế thừa SampleRecipe để màn Khám phá dùng lại danh sách, bảng xem trước và nút "Lưu vào sổ" của món mẫu.
 * id (của SampleRecipe) = "c-" + postId → khi lưu vào sổ được ghi vào Recipe.sourceSampleId,
 * nhờ vậy app biết bài nào đã lưu (nhãn "Đã lưu") giống hệt món mẫu.
 */
public class CommunityRecipe extends SampleRecipe {

    /** Tiền tố phân biệt bài cộng đồng với món mẫu trong assets (món mẫu có mã dạng "m01"). */
    public static final String ID_PREFIX = "c-";

    public String postId = "";      // mã document = "{uid tác giả}_{cloudId của món}"
    public String authorUid = "";
    public String authorName = "";
    public long postedAt;           // thời điểm đăng (mili giây), dùng để xếp bài mới nhất lên đầu

    public boolean isMine(String uid) {
        return uid != null && uid.equals(authorUid);
    }
}
