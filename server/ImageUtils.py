from PIL import Image
#img utils

def ahash(frame, res = 16):
	i = Image.fromarray(frame)
	i = i.resize((res,res), Image.ANTIALIAS).convert('L')
	pixels = list(i.getdata())
	avg = sum(pixels)/len(pixels)
	bits = "".join(map(lambda pixel: '1' if pixel < avg else '0', pixels))
	hexadecimal = int(bits, 2).__format__('016x').upper()
	return hexadecimal