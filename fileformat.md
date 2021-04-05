# Cryptocam File format

Cryptocam output files are encrypted through `age` and contain:
 - 4B Magic number (0x1c5a8e9f)
 - 2B Version number (Incrementing, starts at 0)
 - 1B number of recipients
 - up to 20 SHA256 hashes (truncated to last 16 bytes) of the `age` X25519 public keys the file is encrypted to

From here on everything is encrypted through `age`
 
 - a header
 - some metadata fields
 - encoded video and audio buffers
 
All values are little-endian.

## Header

 - + 00 1B File type (1 for video h264/mp4als, 2 for photo jpeg)
 - + 02 4B Offset from start of file to data
 
## Metadata

Directly after the header
Just a JSON string that can contain fields like:
 - timestamp
 - coordinates (maybe in the future)

## Data

### h264/mp4als video

Each buffer starts with 1 byte describing it's type:
 - 1 for video
 - 2 for audio

8 bytes pts in microseconds
 
4 bytes for buffer length

Buffer data

### jpeg photo

Just the entire jpeg file