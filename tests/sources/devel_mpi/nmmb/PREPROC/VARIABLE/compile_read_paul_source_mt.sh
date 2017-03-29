#!/bin/bash 

  # Get parameters
  src=$1
  dest=$2

  # Compile font (linking to libraries)
  echo "[RPS_SH] Compile F90 file"
  ifort \
    -traceback \
    -assume byterecl \
    -O3 \
    -fp-model precise \
    -fp-stack-check \
    -mcmodel=large \
    -shared-intel \
    -convert big_endian \
    -I/gpfs/apps/MN3/NETCDF/3.6.3/include \
    -L/gpfs/apps/MN3/NETCDF/3.6.3/lib \
    -lnetcdff \
    -lnetcdf \
    -L/gpfs/apps/NVIDIA/HDF5/1.8.8/lib/ \
    -lhdf5 \
    -lhdf5_hl \
    -lhdf5_fortran \
    -lhdf5_hl_fortran \
    ${src} \
    -o ${dest}

  # End
  exit
