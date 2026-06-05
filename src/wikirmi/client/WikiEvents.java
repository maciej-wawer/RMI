package wikirmi.client;

import wikirmi.common.dto.LockInfoDTO;
import wikirmi.common.dto.PageSummaryDTO;

/** GUI-facing listener for server push events. {@link ClientCallbackImpl} forwards to this on the EDT. */
public interface WikiEvents {
    void pageCreated(PageSummaryDTO page);
    void pageChanged(PageSummaryDTO page);
    void pageDeleted(String title);
    void lockChanged(String title, LockInfoDTO lock);
    void presenceChanged();
}
