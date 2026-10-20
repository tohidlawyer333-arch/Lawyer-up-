$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$out = Join-Path $PSScriptRoot '..\desktop\Payesh.Desktop\payesh.ico'
$out = [System.IO.Path]::GetFullPath($out)
$bmp = New-Object System.Drawing.Bitmap 64,64
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.Clear([System.Drawing.Color]::FromArgb(16,42,67))
$teal = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(72,214,198)), 3
$gold = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(240,205,112)), 2.5
$white = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)
$font = New-Object System.Drawing.Font 'Segoe UI', 25, ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
$g.DrawLine($teal,32,8,32,43); $g.DrawLine($teal,15,20,49,20)
$g.DrawLine($gold,18,21,10,35); $g.DrawLine($gold,18,21,26,35); $g.DrawArc($gold,8,29,20,12,0,180)
$g.DrawLine($gold,46,21,38,35); $g.DrawLine($gold,46,21,54,35); $g.DrawArc($gold,36,29,20,12,0,180)
$g.DrawLine($teal,23,45,41,45); $g.DrawString('T',$font,$white,22,38)
$stream = New-Object System.IO.MemoryStream
$handle = $bmp.GetHicon(); $icon = [System.Drawing.Icon]::FromHandle($handle); $icon.Save($stream)
[System.IO.File]::WriteAllBytes($out,$stream.ToArray())
$stream.Dispose(); $icon.Dispose(); $font.Dispose(); $white.Dispose(); $gold.Dispose(); $teal.Dispose(); $g.Dispose(); $bmp.Dispose()
Write-Output "Created $out"
