package wikirmi.client;

import wikirmi.common.dto.*;

/** GUI-facing listener for server push events. {@link ClientCallbackImpl} forwards to this on the EDT. */
public interface WikiEvents {
    void pageCreated(Dto.PageSummary page);
    void pageChanged(Dto.PageSummary page);
    void pageDeleted(String title);
    void lockChanged(String title, Dto.LockInfo lock);
    void presenceChanged();
}
