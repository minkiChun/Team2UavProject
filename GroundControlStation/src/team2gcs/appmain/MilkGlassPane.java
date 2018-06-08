package team2gcs.appmain;


import javafx.animation.AnimationTimer;
import javafx.geometry.Rectangle2D;
import javafx.scene.SnapshotParameters;
import javafx.scene.effect.BoxBlur;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

/**
 *
 * @author Michael Hoffer &lt;info@michaelhoffer.de&gt;
 */
public class MilkGlassPane extends Region {
 
    // circle container
    private final Pane container;

    // blur (milk glass effect)
    private final BoxBlur blur;
    private final double initialBlur = 15;

    // background image
    private WritableImage image;


    public MilkGlassPane(Pane container) {

        // milk glass effect:
        // set the blur and color adjust effects
        blur = new BoxBlur(initialBlur, initialBlur, 3);
        setEffect(blur);
        blur.setInput(new ColorAdjust(0, 0, 0.4, 0.0));

        // circle container
        this.container = container;

        // update the background every pulse
        AnimationTimer timer = new AnimationTimer() {

            @Override
            public void handle(long time) {
                updateBackground();
            }
        };

        timer.start();

    }

    /**
     * Updates the background. Create a snapshot of the circle container that
     * fits exactly this pane's bounds and updates the background.
     */
    private void updateBackground() {

        // return null if this pane is invisible
        if (getWidth() < 1
                || getHeight() < 1
                || this.getOpacity() == 0) {
            return;
        }

        // creates a new writable image and update background if dimensions do not match
        if (image == null
                || getWidth() != image.getWidth()
                || getHeight() != image.getHeight()) {
            
            image = new WritableImage(
                    (int) (this.getWidth()),
                    (int) (this.getHeight()));

            // create the backgrouhnd image
            BackgroundImage backgroundImg = new BackgroundImage(image,
                    BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT,
                    BackgroundPosition.CENTER, BackgroundSize.DEFAULT);

            setBackground(new Background(backgroundImg));
        }

        // x, y location of this pane
        double x = getLayoutX();
        double y = getLayoutY();

        // create the snapshot parameters (defines viewport)
        SnapshotParameters sp = new SnapshotParameters();
        Rectangle2D rect = new Rectangle2D(
                x,
                y,
                getWidth(),
                getHeight());
        sp.setViewport(rect);

        // create the snapshot
        container.snapshot(sp, image);

    }
}