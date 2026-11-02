import sys
out=sys.argv[1]
objs=[]
objs.append("<< /Type /Catalog /Pages 2 0 R /AcroForm << /Fields [5 0 R 6 0 R 7 0 R] /NeedAppearances true /DA (/Helv 12 Tf 0 g) /DR << /Font << /Helv 8 0 R >> >> >> >>")
objs.append("<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
objs.append("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /Helv 8 0 R >> >> /Annots [5 0 R 6 0 R 7 0 R] >>")
stream=b"BT /Helv 20 Tf 50 780 Td (QA Form Test) Tj ET BT /Helv 12 Tf 50 720 Td (Full name:) Tj ET BT /Helv 12 Tf 50 670 Td (Email:) Tj ET BT /Helv 12 Tf 50 620 Td (Agree:) Tj ET"
objs.append(b"<< /Length %d >>\nstream\n" % len(stream) + stream + b"\nendstream")
objs.append("<< /Type /Annot /Subtype /Widget /FT /Tx /T (fullname) /Rect [150 710 450 735] /F 4 /P 3 0 R /DA (/Helv 12 Tf 0 g) /MK << /BC [0 0 0] >> >>")
objs.append("<< /Type /Annot /Subtype /Widget /FT /Tx /T (email) /Rect [150 660 450 685] /F 4 /P 3 0 R /DA (/Helv 12 Tf 0 g) /MK << /BC [0 0 0] >> >>")
objs.append("<< /Type /Annot /Subtype /Widget /FT /Btn /T (agree) /V /Off /AS /Off /Rect [150 612 168 630] /F 4 /P 3 0 R /MK << /BC [0 0 0] >> /AP << /N << /Yes 9 0 R /Off 10 0 R >> >> >>")
objs.append("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
yes=b"0 0 0 rg 3 3 12 12 re f"
objs.append(b"<< /Type /XObject /Subtype /Form /BBox [0 0 18 18] /Length %d >>\nstream\n" % len(yes) + yes + b"\nendstream")
objs.append(b"<< /Type /XObject /Subtype /Form /BBox [0 0 18 18] /Length 0 >>\nstream\n\nendstream")
data=b"%PDF-1.4\n"; offs=[]
for i,o in enumerate(objs,1):
    if isinstance(o,str): o=o.encode()
    offs.append(len(data)); data+=b"%d 0 obj\n" % i + o + b"\nendobj\n"
x=len(data); data+=b"xref\n0 %d\n0000000000 65535 f \n" % (len(objs)+1)
for o in offs: data+=b"%010d 00000 n \n" % o
data+=b"trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (len(objs)+1, x)
open(out,"wb").write(data)
