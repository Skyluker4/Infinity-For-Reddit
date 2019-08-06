package ml.docilealligator.infinityforreddit;

public class SubmitImagePostEvent {
    public boolean postSuccess;
    public String errorMessage;

    public SubmitImagePostEvent(boolean postSuccess, String errorMessage) {
        this.postSuccess = postSuccess;
        this.errorMessage = errorMessage;
    }
}
