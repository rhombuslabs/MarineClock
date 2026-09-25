# Builds the adaptive launcher icon layers from tools/icon/ships_bell_icon.jpg:
#   app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png  (gold bell on transparent, 432 px = 108 dp)
#   app/src/main/res/mipmap-xxxhdpi/ic_launcher_monochrome.png  (white bell silhouette, for themed icons)
# The navy background layer is the vector gradient in res/drawable/ic_launcher_background.xml.
# The bell is separated from the navy by colour (gold has red well above blue; navy the reverse), with edge
# pixels un-blended against the local background colour so no blue fringe remains.
param(
    [string]$Preview = ""   # optional path for a circle-masked preview PNG
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$source = Join-Path $PSScriptRoot "icon\ships_bell_icon.jpg"
$outDir = Join-Path $root "app\src\main\res\mipmap-xxxhdpi"
New-Item -ItemType Directory -Force $outDir | Out-Null

Add-Type -AssemblyName System.Drawing
$drawingRefs = @("System.Drawing", "System.Drawing.Primitives", "System.Drawing.Common") +
    @([AppDomain]::CurrentDomain.GetAssemblies() | Where-Object { $_.GetName().Name -like "System.Private.Windows.*" } | ForEach-Object { $_.Location })
Add-Type -ReferencedAssemblies $drawingRefs -TypeDefinition @"
using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

public static class BellIcon {
    // Region inside the source's gold frame and rounded corners (source is 2048 px square).
    const int Inner0 = 280, Inner1 = 1768;
    // Column that is always background, used as the per-row navy reference.
    const int BgColumn = 330;

    static int[] Read(Bitmap b) {
        var d = b.LockBits(new Rectangle(0, 0, b.Width, b.Height), ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
        var px = new int[b.Width * b.Height];
        Marshal.Copy(d.Scan0, px, 0, px.Length);
        b.UnlockBits(d);
        return px;
    }

    static Bitmap Write(int[] px, int w, int h) {
        var b = new Bitmap(w, h, PixelFormat.Format32bppArgb);
        var d = b.LockBits(new Rectangle(0, 0, w, h), ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
        Marshal.Copy(px, 0, d.Scan0, px.Length);
        b.UnlockBits(d);
        return b;
    }

    // Returns the bell cut out of the source, cropped to its bounding box.
    public static Bitmap Extract(string path) {
        using (var src = new Bitmap(path)) {
            int w = src.Width, h = src.Height;
            var px = Read(src);
            var outPx = new int[w * h];
            int minX = w, minY = h, maxX = 0, maxY = 0;
            for (int y = Inner0; y < Inner1; y++) {
                int bg = px[y * w + BgColumn];
                double bgR = (bg >> 16) & 255, bgG = (bg >> 8) & 255, bgB = bg & 255;
                for (int x = Inner0; x < Inner1; x++) {
                    int p = px[y * w + x];
                    double r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
                    double a = Math.Max(0, Math.Min(1, (r - b + 60) / 60.0));
                    if (a <= 0) continue;
                    // Un-blend the navy from edge pixels: p = a*fg + (1-a)*bg.
                    int fr = Clamp((r - (1 - a) * bgR) / a), fg = Clamp((g - (1 - a) * bgG) / a), fb = Clamp((b - (1 - a) * bgB) / a);
                    outPx[y * w + x] = ((int)Math.Round(a * 255) << 24) | (fr << 16) | (fg << 8) | fb;
                    if (a > 0.5) {
                        if (x < minX) minX = x; if (x > maxX) maxX = x;
                        if (y < minY) minY = y; if (y > maxY) maxY = y;
                    }
                }
            }
            using (var full = Write(outPx, w, h)) {
                return full.Clone(new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1), PixelFormat.Format32bppArgb);
            }
        }
    }

    static int Clamp(double v) { return (int)Math.Max(0, Math.Min(255, Math.Round(v))); }

    // Draws [bell] centred on a transparent square canvas, scaled to [heightFraction] of the canvas.
    public static Bitmap Layer(Bitmap bell, int size, double heightFraction, bool white) {
        var canvas = new Bitmap(size, size, PixelFormat.Format32bppArgb);
        double scale = size * heightFraction / bell.Height;
        int dw = (int)Math.Round(bell.Width * scale), dh = (int)Math.Round(bell.Height * scale);
        using (var g = Graphics.FromImage(canvas)) {
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            g.CompositingQuality = CompositingQuality.HighQuality;
            var dest = new Rectangle((size - dw) / 2, (size - dh) / 2, dw, dh);
            if (white) {
                var cm = new ColorMatrix(new float[][] {
                    new float[] {0,0,0,0,0}, new float[] {0,0,0,0,0}, new float[] {0,0,0,0,0},
                    new float[] {0,0,0,1,0}, new float[] {1,1,1,0,1} });
                var ia = new ImageAttributes(); ia.SetColorMatrix(cm);
                g.DrawImage(bell, dest, 0, 0, bell.Width, bell.Height, GraphicsUnit.Pixel, ia);
            } else {
                g.DrawImage(bell, dest, 0, 0, bell.Width, bell.Height, GraphicsUnit.Pixel);
            }
        }
        return canvas;
    }

    // Launcher-style preview: gradient background + foreground, masked to the 72 dp visible circle.
    public static Bitmap Preview(Bitmap fg, Color top, Color bottom) {
        int size = fg.Width, view = size * 72 / 108, off = (size - view) / 2;
        var outBmp = new Bitmap(view, view, PixelFormat.Format32bppArgb);
        using (var g = Graphics.FromImage(outBmp)) {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.Clear(Color.White);
            using (var path = new GraphicsPath()) {
                path.AddEllipse(0, 0, view - 1, view - 1);
                g.SetClip(path);
                using (var brush = new LinearGradientBrush(new Rectangle(0, -off, view, size), top, bottom, 90f)) {
                    g.FillRectangle(brush, 0, 0, view, view);
                }
                g.DrawImage(fg, -off, -off, size, size);
            }
        }
        return outBmp;
    }
}
"@

$bellHeight = 0.50  # bell (with rope) height as a fraction of the 108 dp canvas; keeps it inside the 66 dp safe circle
$bell = [BellIcon]::Extract($source)
"extracted bell: $($bell.Width) x $($bell.Height) px"
$fg = [BellIcon]::Layer($bell, 432, $bellHeight, $false)
$fg.Save((Join-Path $outDir "ic_launcher_foreground.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$mono = [BellIcon]::Layer($bell, 432, $bellHeight, $true)
$mono.Save((Join-Path $outDir "ic_launcher_monochrome.png"), [System.Drawing.Imaging.ImageFormat]::Png)
if ($Preview) {
    $p = [BellIcon]::Preview($fg, [System.Drawing.Color]::FromArgb(0x08, 0x6A, 0x97), [System.Drawing.Color]::FromArgb(0x08, 0x2F, 0x50))
    $p.Save($Preview, [System.Drawing.Imaging.ImageFormat]::Png)
    "preview: $Preview"
}
"wrote ic_launcher_foreground.png and ic_launcher_monochrome.png"
