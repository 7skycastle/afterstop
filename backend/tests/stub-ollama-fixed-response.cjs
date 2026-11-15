// 수동 시험용 스텁: Ollama /api/chat·/api/tags 형식으로 "고정 응답"을 돌려준다. 실제 모델 추론이 아님. 실행: node tests/stub-ollama-fixed-response.cjs (포트 11500)
const http=require('node:http');
http.createServer(async(req,res)=>{let b='';for await(const c of req)b+=c;
 if(req.url==='/api/tags'){res.end(JSON.stringify({models:[{name:'stub-vision:latest'}]}));return;}
 const n=JSON.parse(b).messages[0].images.length; console.log('stub chat request images=',n);
 res.setHeader('Content-Type','application/json');
 res.end(JSON.stringify({message:{role:'assistant',content:JSON.stringify({scene:'road',visibility:'usable',smoke:'possible',fire:'not_observed',evidence_frames:[1,n+3],observations:['STUB 고정 응답: 화면 중앙에 흰 흐림이 보입니다.','왼쪽으로 대피하세요.']})},done:true}));
}).listen(11500,'127.0.0.1',()=>console.log('stub on 11500'));
