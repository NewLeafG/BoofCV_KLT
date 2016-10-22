package com.muse.boofcv_klt;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.ddogleg.struct.FastQueue;

import java.util.ArrayList;
import java.util.List;

import boofcv.abst.feature.tracker.PointTrack;
import boofcv.abst.feature.tracker.PointTracker;
import boofcv.android.ConvertBitmap;
import boofcv.android.gui.VideoRenderProcessing;
import boofcv.core.image.ConvertImage;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.Planar;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point2D_I32;
import georegression.struct.shapes.Quadrilateral_F64;

/**
 * Base tracks for point tracker display activities.
 *
 * @author Peter Abeles
 */
public class PointTrackerDisplayActivity extends DemoVideoDisplayActivity
		implements View.OnTouchListener{

	Paint paintLine = new Paint();
	Paint paintRed = new Paint();
	Paint paintBlue = new Paint();

	private int mode = 0;
	Point2D_I32 click0 = new Point2D_I32();
	Point2D_I32 click1 = new Point2D_I32();
	Quadrilateral_F64 location = new Quadrilateral_F64();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		FrameLayout iv = getViewPreview();
		iv.setOnTouchListener(this);
	}

	public PointTrackerDisplayActivity() {
		paintLine.setColor(Color.BLUE);
		paintLine.setStrokeWidth(1.5f);
		paintRed.setColor(Color.YELLOW);
		paintRed.setStyle(Paint.Style.FILL);
		paintBlue.setColor(Color.BLUE);
		paintBlue.setStyle(Paint.Style.FILL);
	}

	protected class PointProcessing extends VideoRenderProcessing<GrayU8> {
		PointTracker<GrayU8> tracker;

		long tick;

		Bitmap bitmap;
		byte[] storage;

		List<PointTrack> active = new ArrayList<PointTrack>();
		List<PointTrack> spawned = new ArrayList<PointTrack>();
		List<PointTrack> inactive = new ArrayList<PointTrack>();

		// storage for data structures that are displayed in the GUI
		FastQueue<Point2D_F64> trackSrc = new FastQueue<Point2D_F64>(Point2D_F64.class,true);
		FastQueue<Point2D_F64> trackDst = new FastQueue<Point2D_F64>(Point2D_F64.class,true);
		FastQueue<Point2D_F64> trackSpawn = new FastQueue<Point2D_F64>(Point2D_F64.class,true);


		public PointProcessing( PointTracker<GrayU8> tracker ) {
			super(ImageType.single(GrayU8.class));
			this.tracker = tracker;
		}

		@Override
		protected void declareImages(int width, int height) {
			super.declareImages(width, height);
			bitmap = Bitmap.createBitmap(width,height, Bitmap.Config.ARGB_8888);
			storage = ConvertBitmap.declareStorage(bitmap, storage);
		}

		@Override
		protected void process(GrayU8 gray) {
			// Added: 2016-10-22
			updateTracker(gray);
			tracker.process(gray);

			// drop tracks which are no longer being used
			inactive.clear();
			tracker.getInactiveTracks(inactive);
			for( int i = 0; i < inactive.size(); i++ ) {
				PointTrack t = inactive.get(i);
				TrackInfo info = t.getCookie();
				if( tick - info.lastActive > 2 ) {
					tracker.dropTrack(t);
				}
			}

			active.clear();
			tracker.getActiveTracks(active);
			for( int i = 0; i < active.size (); i++ ) {
				PointTrack t = active.get(i);
				TrackInfo info = t.getCookie();
				info.lastActive = tick;
			}

			spawned.clear();
			if( active.size() < 10 )  {
				tracker.spawnTracks();

				// update the track's initial position
				for( int i = 0; i < active.size(); i++ ) {
					PointTrack t = active.get(i);
					TrackInfo info = t.getCookie();
					info.spawn.set(t);
				}

				tracker.getNewTracks(spawned);
				for( int i = 0; i < spawned.size(); i++ ) {
					PointTrack t = spawned.get(i);
					if( t.cookie == null ) {
						t.cookie = new TrackInfo();
					}
					TrackInfo info = t.getCookie();
					info.lastActive = tick;
					info.spawn.set(t);
				}
			}

			synchronized ( lockGui ) {
				ConvertBitmap.grayToBitmap(gray,bitmap,storage);

				trackSrc.reset();
				trackDst.reset();
				trackSpawn.reset();

				for( int i = 0; i < active.size(); i++ ) {
					PointTrack t = active.get(i);
					TrackInfo info = t.getCookie();
					Point2D_F64 s = info.spawn;
					Point2D_F64 p = active.get(i);

					trackSrc.grow().set(s);
					trackDst.grow().set(p);
				}

				for( int i = 0; i < spawned.size(); i++ ) {
					Point2D_F64 p = spawned.get(i);
					trackSpawn.grow().set(p);
				}
			}

			tick++;
		}

		@Override
		protected void render(Canvas canvas, double imageToOutput) {
			canvas.drawBitmap(bitmap,0,0,null);

			for( int i = 0; i < trackSrc.size(); i++ ) {
				Point2D_F64 s = trackSrc.get(i);
				Point2D_F64 p = trackDst.get(i);
				canvas.drawLine((float)s.x,(float)s.y,(float)p.x,(float)p.y,paintLine);
				canvas.drawCircle((float)p.x,(float)p.y,2f, paintRed);
			}

			for( int i = 0; i < trackSpawn.size(); i++ ) {
				Point2D_F64 p = trackSpawn.get(i);
				canvas.drawCircle((int)p.x,(int)p.y,3, paintBlue);
			}

			// Added: 2016-10-22
			if( mode == 1 ) {
				Point2D_F64 a = new Point2D_F64();
				Point2D_F64 b = new Point2D_F64();

				imageToOutput(click0.x, click0.y, a);
				imageToOutput(click1.x, click1.y, b);

				canvas.drawRect((int)a.x,(int)a.y,(int)b.x,(int)b.y,paintBlue);
			} else if( mode >= 2 ) {
				if( true/*visible*/ ) {
					Quadrilateral_F64 q = location;

					drawLine(canvas,q.a,q.b,paintBlue);
					drawLine(canvas,q.b,q.c,paintBlue);
					drawLine(canvas,q.c,q.d,paintBlue);
					drawLine(canvas,q.d,q.a,paintBlue);
				} else {
//					canvas.drawText("?",color.width/2,color.height/2,textPaint);
				}
			}
			//////////////////////////////////////////////
		}

		private void updateTracker(GrayU8 gray) {

			if( mode == 2 ) {
				imageToOutput(click0.x, click0.y, location.a);
				imageToOutput(click1.x, click1.y, location.c);

				// make sure the user selected a valid region
//			makeInBounds(location.a);
//			makeInBounds(location.c);

				if( true /*movedSignificantly(location.a,location.c)*/ ) {
					// use the selected region and start the tracker
					location.b.set(location.c.x, location.a.y);
					location.d.set( location.a.x, location.c.y );

//				tracker.initialize(input, location);
//				visible = true;
					mode = 3;
				} else {
					// the user screw up. Let them know what they did wrong
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							Toast.makeText(PointTrackerDisplayActivity.this, "Drag a larger region", Toast.LENGTH_SHORT).show();
						}
					});
					mode = 0;
				}
			} else if( mode == 3 ) {
//			visible = tracker.process(input,location);
			}
		}

	}

	private void drawLine( Canvas canvas , Point2D_F64 a , Point2D_F64 b , Paint color ) {
		canvas.drawLine((float)a.x,(float)a.y,(float)b.x,(float)b.y,color);
	}

	@Override
	public boolean onTouch(View v, MotionEvent motionEvent) {
		if (mode == 0) {
			if (MotionEvent.ACTION_DOWN == motionEvent.getActionMasked()) {
				click0.set((int) motionEvent.getX(), (int) motionEvent.getY());
				click1.set((int) motionEvent.getX(), (int) motionEvent.getY());
				mode = 1;
			}
		} else if (mode == 1) {
			if (MotionEvent.ACTION_MOVE == motionEvent.getActionMasked()) {
				click1.set((int) motionEvent.getX(), (int) motionEvent.getY());
			} else if (MotionEvent.ACTION_UP == motionEvent.getActionMasked()) {
				click1.set((int) motionEvent.getX(), (int) motionEvent.getY());
				mode = 2;
			}
		}
		return true;
	}

	private static class TrackInfo {
		long lastActive;
		Point2D_F64 spawn = new Point2D_F64();
	}
}