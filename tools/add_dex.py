import shutil
import sys
import zipfile

src, dex, dst = sys.argv[1], sys.argv[2], sys.argv[3]
shutil.copyfile(src, dst)
with zipfile.ZipFile(dst, "a", zipfile.ZIP_DEFLATED) as archive:
    archive.write(dex, "classes.dex")
print("added classes.dex ->", dst)
