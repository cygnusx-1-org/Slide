package me.edgan.redditslide.util;

import android.graphics.Point;
import android.graphics.Rect;
import me.edgan.redditslide.Views.SubsamplingScaleImageView;
import me.edgan.redditslide.Views.SubsamplingScaleImageView.Tile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class for managing tiles in SubsamplingScaleImageView.
 */
public class TileManager {

    /**
     * Once source image and view dimensions are known, creates a map of sample size to tile grid.
     */
    public static void initialiseTileMap(SubsamplingScaleImageView view, Point maxTileDimensions) {
        view.debug("initialiseTileMap maxTileDimensions=%dx%d", maxTileDimensions.x, maxTileDimensions.y);
        view.tileMap = new LinkedHashMap<>();
        int sampleSize = view.fullImageSampleSize;
        int xTiles = 1;
        int yTiles = 1;
        while (true) {
            int sTileWidth = SubsamplingScaleImageViewStateHelper.sWidth(view) / xTiles;
            int sTileHeight = SubsamplingScaleImageViewStateHelper.sHeight(view) / yTiles;
            int subTileWidth = sTileWidth / sampleSize;
            int subTileHeight = sTileHeight / sampleSize;

            while (subTileWidth + xTiles + 1 > maxTileDimensions.x
                    || (subTileWidth > view.getWidth() * 1.25 && sampleSize < view.fullImageSampleSize)) {
                xTiles += 1;
                sTileWidth = SubsamplingScaleImageViewStateHelper.sWidth(view) / xTiles;
                subTileWidth = sTileWidth / sampleSize;
            }

            while (subTileHeight + yTiles + 1 > maxTileDimensions.y
                    || (subTileHeight > view.getHeight() * 1.25 && sampleSize < view.fullImageSampleSize)) {
                yTiles += 1;
                sTileHeight = SubsamplingScaleImageViewStateHelper.sHeight(view) / yTiles;
                subTileHeight = sTileHeight / sampleSize;
            }

            List<Tile> tileGrid = new ArrayList<>(xTiles * yTiles);

            for (int x = 0; x < xTiles; x++) {
                for (int y = 0; y < yTiles; y++) {
                    Tile tile = new Tile();
                    tile.sampleSize = sampleSize;
                    tile.visible = sampleSize == view.fullImageSampleSize;
                    tile.sRect =
                            new Rect(
                                    x * sTileWidth,
                                    y * sTileHeight,
                                    x == xTiles - 1 ? SubsamplingScaleImageViewStateHelper.sWidth(view) : (x + 1) * sTileWidth,
                                    y == yTiles - 1 ? SubsamplingScaleImageViewStateHelper.sHeight(view) : (y + 1) * sTileHeight);
                    tile.vRect = new Rect(0, 0, 0, 0);
                    tile.fileSRect = new Rect(tile.sRect);
                    tileGrid.add(tile);
                }
            }

            view.tileMap.put(sampleSize, tileGrid);

            if (sampleSize == 1) {
                break;
            } else {
                sampleSize /= 2;
            }
        }
    }


    /**
     * Loads the optimum tiles for display at the current scale and translate, so the screen can be
     * filled with tiles that are at least as high resolution as the screen. Frees up bitmaps that
     * are now off the screen.
     *
     * @param view The SubsamplingScaleImageView instance.
     * @param load Whether to load the new tiles needed. Use false while scrolling/panning for
     *     performance.
     */
    public static void refreshRequiredTiles(SubsamplingScaleImageView view, boolean load) {
        if (view.decoder == null || view.tileMap == null) {
            return;
        }

        int sampleSize = Math.min(view.fullImageSampleSize, view.calculateInSampleSize(view.scale));

        // Load tiles of the correct sample size that are on screen. Discard tiles off screen, and those that are higher
        // resolution than required, or lower res than required but not the base layer, so the base layer is always present.
        for (Map.Entry<Integer, List<Tile>> tileMapEntry : view.tileMap.entrySet()) {
            for (Tile tile : tileMapEntry.getValue()) {
                if (tile.sampleSize < sampleSize
                        || (tile.sampleSize > sampleSize
                                && tile.sampleSize != view.fullImageSampleSize)) {
                    tile.visible = false;
                    if (tile.bitmap != null) {
                        tile.bitmap.recycle();
                        tile.bitmap = null;
                    }
                }
                if (tile.sampleSize == sampleSize) {
                    if (TileManager.tileVisible(view, tile)) { // Updated call
                        tile.visible = true;
                        if (!tile.loading && tile.bitmap == null && load) {
                            // Need access to TileLoadTask, assume it's accessible or adjust visibility
                            // For now, assuming view.execute can handle it or we adjust TileLoadTask later
                            SubsamplingScaleImageView.TileLoadTask task = new SubsamplingScaleImageView.TileLoadTask(view, view.decoder, tile);
                            view.execute(task); // Assuming execute is public or package-private
                        }
                    } else if (tile.sampleSize != view.fullImageSampleSize) {
                        tile.visible = false;
                        if (tile.bitmap != null) {
                            tile.bitmap.recycle();
                            tile.bitmap = null;
                        }
                    }
                } else if (tile.sampleSize == view.fullImageSampleSize) {
                    tile.visible = true;
                }
            }
        }
    }

    /**
     * Determine whether tile is visible.
     * @param view The SubsamplingScaleImageView instance.
     * @param tile The tile to check.
     * @return true if the tile is visible.
     * */
    public static boolean tileVisible(SubsamplingScaleImageView view, Tile tile) {
        float sVisLeft = SubsamplingScaleImageViewStateHelper.viewToSourceX(view, 0),
                sVisRight = SubsamplingScaleImageViewStateHelper.viewToSourceX(view, view.getWidth()),
                sVisTop = SubsamplingScaleImageViewStateHelper.viewToSourceY(view, 0),
                sVisBottom = SubsamplingScaleImageViewStateHelper.viewToSourceY(view, view.getHeight());

        return !(sVisLeft > tile.sRect.right
                || tile.sRect.left > sVisRight
                || sVisTop > tile.sRect.bottom
                || tile.sRect.top > sVisBottom);
    }

}