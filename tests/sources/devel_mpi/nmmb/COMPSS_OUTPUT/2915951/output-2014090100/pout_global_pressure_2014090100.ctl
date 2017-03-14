** !! Open this ctl file in "gradsnc" (Grads ver 1.9) or "grads" (Grads ver 2.0).
** !! ------------------------------------------------------------------
** !! If the netcdf file is CF compliant, then it can be opened directly
** !! (without ctl file) by "sdfopen netcdf_filename" (except for netcdf files with Native projection).
** !! ------------------------------------------------------------------
** !! This ctl is for a global domain with a regular lat-lon proj. IF proj is native,
** !! then additional syntax line "PDEF" is required.
** !! ------------------------------------------------------------------
** !! Change DSET path  as required
** !! ------------------------------------------------------------------


DSET NMMB-BSC-CTM_2014090100_glob.nc
DTYPE netcdf
UNDEF -99.90

TITLE output of postall_global_pressure.f

XDEF 257 LINEAR -180.0 1.40625
YDEF 181 LINEAR -90.0 1.0
ZDEF 15 LINEAR 100 200 250 300 400 500 600 700 800 850 900 925 950 975 1000
TDEF 06 LINEAR 0Z01Sep2014 3hr

VARS  49
lat 0  t,y,x    grid latitude
lon 0  t,y,x    grid longitude
tsl 15 t,z,y,x T-UMO
hsl 15 t,z,y,x H-UMO
cldfra 15 t,z,y,x CLOUDFRA-UMO
usl_h 15 t,z,y,x U-UMO
vsl_h 15 t,z,y,x V-UMO
slp 0 t,y,x   SLP-UMO
fis 0 t,y,x   FIS
acprec 0 t,y,x   ACPREC-UMO
u10 0 t,y,x   U10-UMO
v10 0 t,y,x   V10-UMO
ps 0 t,y,x   PS-UMO
alwtoa 0 t,y,x   ALWTOA
dust_conc 15 t,z,y,x DUST_CONC
dust_aod_550 0 t,y,x  dust_aod_550
dust_sconc 0 t,y,x  dust_sconc
dust_sconc02 0 t,y,x  dust_sconc02
dust_sconc10 0 t,y,x  dust_sconc10
dust_pm10_sconc10 0 t,y,x  dust_pm10_sconc10
dust_pm25_sconc10 0 t,y,x  dust_pm25_sconc10
dust_wetdep 0 t,y,x  dust_wetdep
dust_wetdep_cuprec 0 t,y,x  dust_wetdep_cuprec
dust_drydep 0 t,y,x  dust_drydep
dust_load 0 t,y,x  dust_load
dust_load_b1 0 t,y,x  dust_load_b1
dust_load_b2 0 t,y,x  dust_load_b2
dust_load_b3 0 t,y,x  dust_load_b3
dust_load_b4 0 t,y,x  dust_load_b4
dust_load_b5 0 t,y,x  dust_load_b5
dust_load_b6 0 t,y,x  dust_load_b6
dust_load_b7 0 t,y,x  dust_load_b7
dust_load_b8 0 t,y,x  dust_load_b8
dust_aod_550_b1 0 t,y,x  dust_aod_550_b1
dust_aod_550_b2 0 t,y,x  dust_aod_550_b2
dust_aod_550_b3 0 t,y,x  dust_aod_550_b3
dust_aod_550_b4 0 t,y,x  dust_aod_550_b4
dust_aod_550_b5 0 t,y,x  dust_aod_550_b5
dust_aod_550_b6 0 t,y,x  dust_aod_550_b6
dust_aod_550_b7 0 t,y,x  dust_aod_550_b7
dust_aod_550_b8 0 t,y,x  dust_aod_550_b8
dust_sconc_b1 0 t,y,x  dust_sconc_b1
dust_sconc_b2 0 t,y,x  dust_sconc_b2
dust_sconc_b3 0 t,y,x  dust_sconc_b3
dust_sconc_b4 0 t,y,x  dust_sconc_b4
dust_sconc_b5 0 t,y,x  dust_sconc_b5
dust_sconc_b6 0 t,y,x  dust_sconc_b6
dust_sconc_b7 0 t,y,x  dust_sconc_b7
dust_sconc_b8 0 t,y,x  dust_sconc_b8
endvars

** !! Open this ctl file in "gradsnc" (Grads ver 1.9) or "grads" (Grads ver 2.0).
** !! ------------------------------------------------------------------
** !! If the netcdf file is CF compliant, then it can be opened directly
** !! (without ctl file) by "sdfopen netcdf_filename" (except for netcdf files with Native projection).
** !! ------------------------------------------------------------------
** !! This ctl is for a global domain with a regular lat-lon proj. IF proj is native,
** !! then additional syntax line "PDEF" is required.
** !! ------------------------------------------------------------------
** !! Change DSET path  as required
** !! ------------------------------------------------------------------


DSET NMMB-BSC-CTM_2014090100_glob.nc
DTYPE netcdf
UNDEF -99.90

TITLE output of postall_global_pressure.f

XDEF 257 LINEAR -180.0 1.40625
YDEF 181 LINEAR -90.0 1.0
ZDEF 15 LINEAR 100 200 250 300 400 500 600 700 800 850 900 925 950 975 1000
TDEF 06 LINEAR 0Z01Sep2014 3hr

VARS  49
lat 0  t,y,x    grid latitude
lon 0  t,y,x    grid longitude
tsl 15 t,z,y,x T-UMO
hsl 15 t,z,y,x H-UMO
cldfra 15 t,z,y,x CLOUDFRA-UMO
usl_h 15 t,z,y,x U-UMO
vsl_h 15 t,z,y,x V-UMO
slp 0 t,y,x   SLP-UMO
fis 0 t,y,x   FIS
acprec 0 t,y,x   ACPREC-UMO
u10 0 t,y,x   U10-UMO
v10 0 t,y,x   V10-UMO
ps 0 t,y,x   PS-UMO
alwtoa 0 t,y,x   ALWTOA
dust_conc 15 t,z,y,x DUST_CONC
dust_aod_550 0 t,y,x  dust_aod_550
dust_sconc 0 t,y,x  dust_sconc
dust_sconc02 0 t,y,x  dust_sconc02
dust_sconc10 0 t,y,x  dust_sconc10
dust_pm10_sconc10 0 t,y,x  dust_pm10_sconc10
dust_pm25_sconc10 0 t,y,x  dust_pm25_sconc10
dust_wetdep 0 t,y,x  dust_wetdep
dust_wetdep_cuprec 0 t,y,x  dust_wetdep_cuprec
dust_drydep 0 t,y,x  dust_drydep
dust_load 0 t,y,x  dust_load
dust_load_b1 0 t,y,x  dust_load_b1
dust_load_b2 0 t,y,x  dust_load_b2
dust_load_b3 0 t,y,x  dust_load_b3
dust_load_b4 0 t,y,x  dust_load_b4
dust_load_b5 0 t,y,x  dust_load_b5
dust_load_b6 0 t,y,x  dust_load_b6
dust_load_b7 0 t,y,x  dust_load_b7
dust_load_b8 0 t,y,x  dust_load_b8
dust_aod_550_b1 0 t,y,x  dust_aod_550_b1
dust_aod_550_b2 0 t,y,x  dust_aod_550_b2
dust_aod_550_b3 0 t,y,x  dust_aod_550_b3
dust_aod_550_b4 0 t,y,x  dust_aod_550_b4
dust_aod_550_b5 0 t,y,x  dust_aod_550_b5
dust_aod_550_b6 0 t,y,x  dust_aod_550_b6
dust_aod_550_b7 0 t,y,x  dust_aod_550_b7
dust_aod_550_b8 0 t,y,x  dust_aod_550_b8
dust_sconc_b1 0 t,y,x  dust_sconc_b1
dust_sconc_b2 0 t,y,x  dust_sconc_b2
dust_sconc_b3 0 t,y,x  dust_sconc_b3
dust_sconc_b4 0 t,y,x  dust_sconc_b4
dust_sconc_b5 0 t,y,x  dust_sconc_b5
dust_sconc_b6 0 t,y,x  dust_sconc_b6
dust_sconc_b7 0 t,y,x  dust_sconc_b7
dust_sconc_b8 0 t,y,x  dust_sconc_b8
endvars

** !! Open this ctl file in "gradsnc" (Grads ver 1.9) or "grads" (Grads ver 2.0).
** !! ------------------------------------------------------------------
** !! If the netcdf file is CF compliant, then it can be opened directly
** !! (without ctl file) by "sdfopen netcdf_filename" (except for netcdf files with Native projection).
** !! ------------------------------------------------------------------
** !! This ctl is for a global domain with a regular lat-lon proj. IF proj is native,
** !! then additional syntax line "PDEF" is required.
** !! ------------------------------------------------------------------
** !! Change DSET path  as required
** !! ------------------------------------------------------------------


DSET NMMB-BSC-CTM_2014090100_glob.nc
DTYPE netcdf
UNDEF -99.90

TITLE output of postall_global_pressure.f

XDEF 257 LINEAR -180.0 1.40625
YDEF 181 LINEAR -90.0 1.0
ZDEF 15 LINEAR 100 200 250 300 400 500 600 700 800 850 900 925 950 975 1000
TDEF 06 LINEAR 0Z01Sep2014 3hr

VARS  49
lat 0  t,y,x    grid latitude
lon 0  t,y,x    grid longitude
tsl 15 t,z,y,x T-UMO
hsl 15 t,z,y,x H-UMO
cldfra 15 t,z,y,x CLOUDFRA-UMO
usl_h 15 t,z,y,x U-UMO
vsl_h 15 t,z,y,x V-UMO
slp 0 t,y,x   SLP-UMO
fis 0 t,y,x   FIS
acprec 0 t,y,x   ACPREC-UMO
u10 0 t,y,x   U10-UMO
v10 0 t,y,x   V10-UMO
ps 0 t,y,x   PS-UMO
alwtoa 0 t,y,x   ALWTOA
dust_conc 15 t,z,y,x DUST_CONC
dust_aod_550 0 t,y,x  dust_aod_550
dust_sconc 0 t,y,x  dust_sconc
dust_sconc02 0 t,y,x  dust_sconc02
dust_sconc10 0 t,y,x  dust_sconc10
dust_pm10_sconc10 0 t,y,x  dust_pm10_sconc10
dust_pm25_sconc10 0 t,y,x  dust_pm25_sconc10
dust_wetdep 0 t,y,x  dust_wetdep
dust_wetdep_cuprec 0 t,y,x  dust_wetdep_cuprec
dust_drydep 0 t,y,x  dust_drydep
dust_load 0 t,y,x  dust_load
dust_load_b1 0 t,y,x  dust_load_b1
dust_load_b2 0 t,y,x  dust_load_b2
dust_load_b3 0 t,y,x  dust_load_b3
dust_load_b4 0 t,y,x  dust_load_b4
dust_load_b5 0 t,y,x  dust_load_b5
dust_load_b6 0 t,y,x  dust_load_b6
dust_load_b7 0 t,y,x  dust_load_b7
dust_load_b8 0 t,y,x  dust_load_b8
dust_aod_550_b1 0 t,y,x  dust_aod_550_b1
dust_aod_550_b2 0 t,y,x  dust_aod_550_b2
dust_aod_550_b3 0 t,y,x  dust_aod_550_b3
dust_aod_550_b4 0 t,y,x  dust_aod_550_b4
dust_aod_550_b5 0 t,y,x  dust_aod_550_b5
dust_aod_550_b6 0 t,y,x  dust_aod_550_b6
dust_aod_550_b7 0 t,y,x  dust_aod_550_b7
dust_aod_550_b8 0 t,y,x  dust_aod_550_b8
dust_sconc_b1 0 t,y,x  dust_sconc_b1
dust_sconc_b2 0 t,y,x  dust_sconc_b2
dust_sconc_b3 0 t,y,x  dust_sconc_b3
dust_sconc_b4 0 t,y,x  dust_sconc_b4
dust_sconc_b5 0 t,y,x  dust_sconc_b5
dust_sconc_b6 0 t,y,x  dust_sconc_b6
dust_sconc_b7 0 t,y,x  dust_sconc_b7
dust_sconc_b8 0 t,y,x  dust_sconc_b8
endvars
