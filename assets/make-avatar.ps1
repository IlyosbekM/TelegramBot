# QarzBot brend-logo avatarini GDI+ bilan chizadi -> bot-avatar.png (512x512)
# Konsept: oltin tanga + aylanma almashinuv strelkalari (qarz berish <-> qaytarish sikli). Harfsiz.
Add-Type -AssemblyName System.Drawing

$size = 512
$bmp  = New-Object System.Drawing.Bitmap($size, $size)
$g    = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode     = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.PixelOffsetMode   = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

# --- Fon: yashil diagonal gradient ---
$rectF = New-Object System.Drawing.RectangleF(0, 0, $size, $size)
$c1 = [System.Drawing.Color]::FromArgb(255, 22, 163, 74)
$c2 = [System.Drawing.Color]::FromArgb(255, 4, 108, 78)
$bgBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rectF, $c1, $c2, 45.0)
$g.FillRectangle($bgBrush, 0, 0, $size, $size)

# --- Yumshoq yorug'lik dog'i (yuqori chap) ---
$glowPath = New-Object System.Drawing.Drawing2D.GraphicsPath
$glowPath.AddEllipse(-90, -130, 440, 440)
$pgb = New-Object System.Drawing.Drawing2D.PathGradientBrush($glowPath)
$pgb.CenterColor    = [System.Drawing.Color]::FromArgb(70, 255, 255, 255)
$pgb.SurroundColors = @([System.Drawing.Color]::FromArgb(0, 255, 255, 255))
$g.FillRectangle($pgb, 0, 0, $size, $size)

# --- Markazdagi oltin tanga ---
$coinD = 300
$coinX = [int](($size - $coinD) / 2)
$coinY = [int](($size - $coinD) / 2)
# soya
$shadow = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(70, 0, 0, 0))
$g.FillEllipse($shadow, $coinX, ($coinY + 12), $coinD, $coinD)
# tana (oltin gradient)
$coinRect = New-Object System.Drawing.RectangleF([float]$coinX, [float]$coinY, [float]$coinD, [float]$coinD)
$gold1 = [System.Drawing.Color]::FromArgb(255, 253, 230, 138)
$gold2 = [System.Drawing.Color]::FromArgb(255, 217, 164, 6)
$coinBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush($coinRect, $gold1, $gold2, 90.0)
$g.FillEllipse($coinBrush, $coinX, $coinY, $coinD, $coinD)
# ichki halqa (tanga relyefi)
$ringPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(140, 161, 98, 7), 9)
$inset = 24
$g.DrawEllipse($ringPen, ($coinX + $inset), ($coinY + $inset), ($coinD - 2*$inset), ($coinD - 2*$inset))

# --- Belgi: aylanma almashinuv strelkalari (ikki yoy + uchlari) ---
$cx = $size / 2
$cy = $coinY + $coinD / 2
$r  = 78.0
$arcRect = New-Object System.Drawing.RectangleF([float]($cx - $r), [float]($cy - $r), [float](2*$r), [float](2*$r))
$markCol = [System.Drawing.Color]::FromArgb(255, 6, 95, 70)   # to'q yashil
$arcPen  = New-Object System.Drawing.Pen($markCol, 26)
$arcPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$arcPen.EndCap   = [System.Drawing.Drawing2D.LineCap]::Round

# yuqori yoy (chapdan o'ngga) va pastki yoy (o'ngdan chapga) — har biri ~150 gradus
$g.DrawArc($arcPen, $arcRect, 200, 150)   # pastki-chap yoy
$g.DrawArc($arcPen, $arcRect, 20, 150)    # yuqori-o'ng yoy

# strelka uchlarini chizish (uchburchak) — yoy oxirlariga
$arrowBrush = New-Object System.Drawing.SolidBrush($markCol)
function Add-Arrow($angleDeg, $dir) {
    $a = [Math]::PI * $angleDeg / 180.0
    $px = $cx + $r * [Math]::Cos($a)
    $py = $cy + $r * [Math]::Sin($a)
    # urinma yo'nalishi
    $t = $a + ($dir * [Math]::PI / 2)
    $len = 30.0; $wid = 22.0
    $tipx = $px + $len * [Math]::Cos($t)
    $tipy = $py + $len * [Math]::Sin($t)
    $n = $t + [Math]::PI/2
    $b1x = $px + $wid * [Math]::Cos($n); $b1y = $py + $wid * [Math]::Sin($n)
    $b2x = $px - $wid * [Math]::Cos($n); $b2y = $py - $wid * [Math]::Sin($n)
    $pts = New-Object System.Drawing.PointF[] 3
    $pts[0] = New-Object System.Drawing.PointF([float]$tipx, [float]$tipy)
    $pts[1] = New-Object System.Drawing.PointF([float]$b1x,  [float]$b1y)
    $pts[2] = New-Object System.Drawing.PointF([float]$b2x,  [float]$b2y)
    $g.FillPolygon($arrowBrush, $pts)
}
# yoy oxirlari: pastki yoy 200+150=350 darajada tugaydi; yuqori yoy 20+150=170 da tugaydi
Add-Arrow 350 1
Add-Arrow 170 1

# --- Pastki o'ng: yashil ✓ doirachasi (to'lov tasdiqlandi) ---
$badgeD = 132
$badgeX = $size - $badgeD - 24
$badgeY = $size - $badgeD - 24
$g.FillEllipse($shadow, $badgeX, ($badgeY + 6), $badgeD, $badgeD)
$badgeBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 16, 185, 129))
$g.FillEllipse($badgeBrush, $badgeX, $badgeY, $badgeD, $badgeD)
$badgeRing = New-Object System.Drawing.Pen([System.Drawing.Color]::White, 8)
$g.DrawEllipse($badgeRing, $badgeX, $badgeY, $badgeD, $badgeD)
$checkPen = New-Object System.Drawing.Pen([System.Drawing.Color]::White, 16)
$checkPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$checkPen.EndCap   = [System.Drawing.Drawing2D.LineCap]::Round
$checkPen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
$cpts = New-Object System.Drawing.PointF[] 3
$cpts[0] = New-Object System.Drawing.PointF([float]($badgeX + 37), [float]($badgeY + 70))
$cpts[1] = New-Object System.Drawing.PointF([float]($badgeX + 59), [float]($badgeY + 92))
$cpts[2] = New-Object System.Drawing.PointF([float]($badgeX + 98), [float]($badgeY + 44))
$g.DrawLines($checkPen, $cpts)

# --- Saqlash ---
$out = Join-Path $PSScriptRoot "bot-avatar.png"
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()
Write-Host "Saqlandi: $out"
