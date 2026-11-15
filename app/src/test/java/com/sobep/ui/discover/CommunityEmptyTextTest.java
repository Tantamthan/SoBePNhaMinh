package com.sobep.ui.discover;

import static org.junit.Assert.assertEquals;

import com.sobep.R;

import org.junit.Test;

/** Câu báo khi tab Cộng đồng trống: chọn đúng theo lý do trống (DiscoverViewModel.communityEmptyTextFor). */
public class CommunityEmptyTextTest {

    @Test
    public void noPosts_invitesFirstPost() {
        assertEquals(R.string.community_empty, DiscoverViewModel.communityEmptyTextFor(false, 0));
    }

    @Test
    public void allPostsReported_saysHidden() {
        assertEquals(R.string.community_empty_all_hidden, DiscoverViewModel.communityEmptyTextFor(false, 3));
    }

    @Test
    public void loadFailed_asksToReload() {
        assertEquals(R.string.community_empty_load_failed, DiscoverViewModel.communityEmptyTextFor(true, 0));
    }

    @Test
    public void loadFailed_winsOverHidden() {
        // Tải lỗi thì không biết trên mây còn bài nào → ưu tiên báo lỗi
        assertEquals(R.string.community_empty_load_failed, DiscoverViewModel.communityEmptyTextFor(true, 2));
    }
}
