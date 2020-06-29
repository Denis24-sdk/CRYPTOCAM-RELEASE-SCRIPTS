# Cryptocam companion script

This is a small utility to decrypt videos made with [Cryptocam](https://gitlab.com/tnibler/cryptocam).

## Usage

Make sure you have installed `pipenv`, `ffmpeg` and `gpg` using your distro's package manager.

Set up the virtual environment:

```
cd path/to/cryptocam-companion
pipenv install
```

Then use `decrypt.sh`:

```
./decrypt.sh /path/to/my/cryptocam-videos/* --destination /some/destination
```

Make sure you have the right keys in your `gpg` keyring, as otherwise decryption is obviously impossible. To override the default GnuPG home directory, pass `--gpg-home <path>` to the script.